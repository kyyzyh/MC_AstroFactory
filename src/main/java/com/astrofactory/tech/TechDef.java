package com.astrofactory.tech;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据驱动科技定义（US-105）。
 *
 * <p>与硬编码的 {@link TechNodes} 不同，本类描述的科技完全来自数据包 JSON：
 * {@code data/<namespace>/astrofactory_tech/<id>.json}。新增一项科技只需丢一个
 * JSON 进数据包，不必改代码、不必重新编译。</p>
 *
 * <p>字段（对应 JSON）：</p>
 * <pre>{@code
 * {
 *   "name": "测试组装",                       // 显示名（可填 lang key 或纯文本）
 *   "parents": ["test_basics"],              // 前置科技 id 列表，可为空数组
 *   "pack_item": "astrofactory:research_pack", // 消耗的科技包物品 id
 *   "pack_count": 5,                         // 需要投入的科技包数量（= 研究成本）
 *   "unlock_recipes": ["astrofactory:test_part"] // 解锁的配方 id 列表
 * }
 * }</pre>
 *
 * <p>文件 id（不含目录与扩展名）就是科技 id，且必须与 {@code namespace} 一起
 * 构成完整 id——本卡统一用 {@code astrofactory} 命名空间。</p>
 *
 * @param id            科技 id（含命名空间，如 {@code astrofactory:test_assembly}）
 * @param name          显示名（lang key 或纯文本）
 * @param parents       前置科技 id 列表
 * @param packItem      消耗的科技包物品 id
 * @param packCount     需要的科技包数量（研究成本）
 * @param unlockRecipes 解锁的配方 id 列表
 */
public record TechDef(
        String id,
        String name,
        List<String> parents,
        String packItem,
        int packCount,
        List<String> unlockRecipes
) {

    /** 从 JSON 解析。缺字段时用安全默认值，格式错误抛 {@link com.google.gson.JsonParseException}。 */
    public static TechDef fromJson(ResourceLocation fileId, JsonObject json) {
        String id = fileId.toString();
        String name = GsonHelper.getAsString(json, "name", id);

        List<String> parents = new ArrayList<>();
        if (json.has("parents")) {
            JsonArray arr = GsonHelper.getAsJsonArray(json, "parents");
            for (JsonElement element : arr) {
                parents.add(element.getAsString());
            }
        }

        String packItem = GsonHelper.getAsString(json, "pack_item", "astrofactory:research_pack");
        int packCount = GsonHelper.getAsInt(json, "pack_count", 1);

        List<String> unlockRecipes = new ArrayList<>();
        if (json.has("unlock_recipes")) {
            JsonArray arr = GsonHelper.getAsJsonArray(json, "unlock_recipes");
            for (JsonElement element : arr) {
                unlockRecipes.add(element.getAsString());
            }
        }

        return new TechDef(id, name, List.copyOf(parents), packItem,
                Math.max(1, packCount), List.copyOf(unlockRecipes));
    }

    /** 序列化成 JSON（DataGen 与调试用）。 */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", this.name);
        if (!this.parents.isEmpty()) {
            JsonArray arr = new JsonArray();
            this.parents.forEach(arr::add);
            json.add("parents", arr);
        }
        json.addProperty("pack_item", this.packItem);
        json.addProperty("pack_count", this.packCount);
        if (!this.unlockRecipes.isEmpty()) {
            JsonArray arr = new JsonArray();
            this.unlockRecipes.forEach(arr::add);
            json.add("unlock_recipes", arr);
        }
        return json;
    }
}
