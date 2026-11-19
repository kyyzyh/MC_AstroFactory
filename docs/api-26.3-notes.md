# NeoForge 26.3 API 实测笔记

来源：`javap` 直接反编译本机 `C:\Users\zyh18\AppData\Roaming\.minecraft\versions\26.3\26.3.jar`（官方映射）
与 `api-ref/neoforge-26.3.0.113-universal.jar`（NeoForge 26.3.0.113）。
**所有签名均实测，不是凭记忆。**

## 1. 重大重命名（相对 1.21.x）

| 旧（1.21.x 及之前） | 新（26.3） |
|---|---|
| `net.minecraft.resources.ResourceLocation` | **`net.minecraft.resources.Identifier`** |
| `BlockEntityType.Builder.of(...)` | **已删除**，改用公开构造器 |

- `ResourceLocation` 在 26.3 **完全不存在**（javap 报「找不到类」）。所有用到的地方都要写 `Identifier`。
- `DeferredHolder.getId()` 返回 `Identifier`；`DeferredRegister.create(Identifier, String)` 也存在。

## 2. 方块实体类型

```java
// 26.3：没有 Builder，直接 new
public BlockEntityType(BlockEntitySupplier<? extends T> supplier, Set<Block> validBlocks)
```

注册：
```java
public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MyBE>> MY_BE =
        BLOCK_ENTITY_TYPES.register("my_be",
                () -> new BlockEntityType<>(MyBE::new, Set.of(MY_BLOCK.get())));
```

## 3. BlockEntity 存档 API（重要变更）

26.3 已从 `CompoundTag` 改为 **`ValueInput` / `ValueOutput`**：

```java
public final void loadWithComponents(ValueInput input);
public final void loadCustomOnly(ValueInput input);
public void saveAdditional(ValueOutput output);      // 覆写这个
public void loadAdditional(ValueInput input);        // 覆写这个
public final CompoundTag saveWithFullMetadata(HolderLookup.Provider);
public final CompoundTag saveWithoutMetadata(HolderLookup.Provider);
public final CompoundTag saveCustomOnly(HolderLookup.Provider);
```

- `getUpdateTag(HolderLookup.Provider)` 返回 `CompoundTag`（未变）。
- `getUpdatePacket()` 返回 `Packet<ClientGamePacketListener>`。

### ValueOutput（写）常用方法
```java
<T> void store(String key, Codec<T> codec, T value);
<T> void storeNullable(String key, Codec<T> codec, T value);
void putBoolean / putByte / putShort / putInt / putLong / putFloat / putDouble / putString(String, ...);
void putIntArray(String, int[]);
ValueOutput child(String key);
ValueOutput.ValueOutputList childrenList(String key);
<T> ValueOutput.TypedOutputList<T> list(String key, Codec<T> codec);
void discard(String key);
```

### ValueInput（读）常用方法
```java
<T> Optional<T> read(String key, Codec<T> codec);
Optional<ValueInput> child(String key);
ValueInput childOrEmpty(String key);
boolean getBooleanOr(String, boolean);
int getIntOr(String, int);   long getLongOr(String, long);
// 同族：getByteOr / getShortOr / getFloatOr / getDoubleOr / getStringOr
Optional<Integer> getInt(String);
<T> ValueInput.TypedInputList<T> listOrEmpty(String key, Codec<T> codec);
```

- **注意**：`ValueInput` 的 `getShortOr(String, short)` 存在但 `getByteOr` 等同样存在；
  没有 `getCompound`，嵌套结构一律用 `child(...)`。
- 物品栏存取推荐 `SimpleContainer.fromItemList(...)` / `storeAsItemList(...)`（见第 7 节），
  比手写循环更安全。

## 4. 注册表键（`net.minecraft.core.registries.Registries`）

字段类型是 `ResourceKey<Registry<T>>`：

- `BLOCK`、`ITEM`、`BLOCK_ENTITY_TYPE`、`MENU`、`CREATIVE_MODE_TAB`、`RECIPE_TYPE`、`RECIPE_SERIALIZER`

`DeferredRegister.create(Registries.BLOCK, MODID)` 仍然可用（有对应重载）。

## 5. DeferredRegister / DeferredHolder

```java
public static <T> DeferredRegister<T> create(Registry<T> registry, String modid);
public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String modid);
public static <T> DeferredRegister<T> create(Identifier registryName, String modid);

public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I>);
public <I extends T> DeferredHolder<T, I> register(String name, Function<Identifier, ? extends I>);
public void register(IEventBus bus);
```

便捷工厂（26.3 新增）：`DeferredRegister.createItems(modid)` / `createBlocks(modid)` /
`createEntities(modid)` / `createDataComponents(key, modid)`。

`DeferredHolder`：
```java
public T value();  public T get();
public Identifier getId();
public boolean isBound();
```

## 6. 菜单（MenuType）

- `MenuType` 构造器是 **private**，只能用 `IMenuTypeExtension.create(IContainerFactory<T>)`：

```java
public static <T extends AbstractContainerMenu> MenuType<T> create(IContainerFactory<T> factory);
// IContainerFactory: T create(int windowId, Inventory inv, RegistryFriendlyByteBuf buf)
```

- 无网络数据的简单菜单可用 `new MenuType<>(MenuSupplier, FeatureFlagSet)` 的等价路径——
  但构造器私有，实际走 `IMenuTypeExtension.create(...)`。

## 7. 容器与槽位

```java
public abstract class BaseContainerBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable {
    public AbstractContainerMenu createMenu(int, Inventory, Player);
    public Component getDisplayName();
    public boolean canOpen(Player);
}

public class SimpleContainer implements Container, StackedContentsCompatible {
    public SimpleContainer(int size);
    public ItemStack addItem(ItemStack);   // 返回剩余
    public ItemStack removeItem(int, int);
    public void setItem(int, ItemStack);
    public NonNullList<ItemStack> getItems();
}

public class Slot {
    public Slot(Container, int index, int x, int y);
    public boolean mayPlace(ItemStack);
    public ItemStack getItem();
}
```

## 8. 创造模式标签页

```java
public static class CreativeModeTab.Builder {
    public Builder(Row row, int column);
    public Builder title(Component);
    public Builder icon(Supplier<ItemStack>);
    public Builder displayItems(DisplayItemsGenerator);
    public CreativeModeTab build();
}
```

## 9. 方块 / 物品

```java
BlockBehaviour.Properties.of()                  // 静态工厂
    .strength(float, float) / .strength(float)
    .noOcclusion() / .noCollision()
    .sound(SoundType) / .mapColor(MapColor)
    .lightLevel(ToIntFunction<BlockState>)

new Block(BlockBehaviour.Properties)
new BlockItem(Block, Item.Properties)
new Item(Item.Properties)
Item.Properties: new Item.Properties(); .stacksTo(int); .rarity(Rarity)
```

## 10. 配方查询

```java
public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>>
    RecipeManager.getRecipeFor(RecipeType<T>, I, Level);
```

## 11. GUI 与容器数据同步（实测，26.3）

`AbstractContainerScreen` 在 26.3 是 **extract 式**渲染，不是旧的 `render(GuiGraphics)`：

```java
public void extractRenderState(GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick);
public void extractContents(GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick);  // 覆写这个画背景+前景
protected void extractLabels(GuiGraphicsExtractor, int xm, int ym);
```

`GuiGraphicsExtractor` 常用方法：

```java
void blit(Identifier, int x, int y, int u, int v, float w, float h, float texW, float texH);
void fill(int x0, int y0, int x1, int y1, int color);
void text(Font, Component, int x, int y, int color, boolean shadow);
```

### 按钮 → 服务端

```java
// 客户端：点按钮发 MenuButtonClick 包
minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
// 服务端：在 AbstractContainerMenu 里接
@Override public boolean clickMenuButton(Player player, int id) { ... }
```

### ContainerData 的正确写法（坑）

`addDataSlots(ContainerData)` 会为每个 index 包一层 `DataSlot.forContainer(container, i)`，
`broadcastChanges()` 里调用 `get()` 再同步。因此：

- **服务端的 `get()` 必须实时读真实数据源**（不要靠 `set()` 写入的镜像），
  否则 `broadcastChanges` 读到的永远是旧值，客户端永远不更新。
- 客户端收到同步包时走 `set()`，所以 `set()` 只写本地镜像数组，`get()` 在客户端读镜像。

推荐实现：

```java
public static final class ProgressData implements ContainerData {
    private final Player player;
    private final int[] mirror = new int[4];
    private boolean isServer() { return player != null && !player.level().isClientSide(); }
    @Override public int get(int i) {
        if (!isServer()) return mirror[i];
        // ...服务端实时计算...
    }
    @Override public void set(int i, int v) { mirror[i] = v; }
    @Override public int getCount() { return 4; }
}
```

## 12. 打开菜单

```java
// Block 里（BaseEntityBlock 的 getMenuProvider 已自动把 BE 的 MenuProvider 交出去）
if (level.getBlockEntity(pos) instanceof MyBlockEntity be) player.openMenu(be);

// 注意：Player.openMenu(MenuProvider) 在客户端是空实现（返回 OptionalInt.empty()），
// 服务端 ServerPlayer 才真正发包。所以只在 !level.isClientSide() 分支调用即可。
```

## 13. ItemStack 判等

```java
stack.getItem() == ModRegistry.MY_ITEM.get()   // 直接比 Item 实例，最简单可靠
stack.is(predicate)                            // 只有 Predicate<Holder<Item>> 重载，没有 is(Item)
```
