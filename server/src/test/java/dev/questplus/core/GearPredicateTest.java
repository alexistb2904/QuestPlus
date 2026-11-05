package dev.questplus.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class GearPredicateTest {
    private static Object item(String id,Map<String,Integer> ench) {
        return Map.of("item",id,"enchantments",ench);
    }
    @Test void exactArmorAndEnchantmentLevelsMustMatchTogether() {
        var requirement=JsonParser.parseString("""
            {"items":[
              {"slot":"head","item":"minecraft:netherite_helmet","enchantments":{"minecraft:protection":4,"minecraft:unbreaking":3}},
              {"slot":"chest","item":"minecraft:netherite_chestplate","enchantments":{"minecraft:protection":4}},
              {"slot":"legs","item":"minecraft:netherite_leggings","enchantments":{"minecraft:protection":4}},
              {"slot":"feet","item":"minecraft:netherite_boots","enchantments":{"minecraft:protection":4}}
            ]}
            """);
        GearPredicate.validate(requirement);
        var context=new HashMap<String,Object>();
        var gear=new HashMap<String,Object>();
        gear.put("head",List.of(item("minecraft:netherite_helmet",Map.of("minecraft:protection",4,"minecraft:unbreaking",3))));
        gear.put("chest",List.of(item("minecraft:netherite_chestplate",Map.of("minecraft:protection",4))));
        gear.put("legs",List.of(item("minecraft:netherite_leggings",Map.of("minecraft:protection",4))));
        gear.put("feet",List.of(item("minecraft:netherite_boots",Map.of("minecraft:protection",4))));
        gear.put("inventory",List.of());
        context.put("gear",gear);
        assertTrue(GearPredicate.test(requirement,context));
        gear.put("head",List.of(item("minecraft:netherite_helmet",Map.of("minecraft:protection",3,"minecraft:unbreaking",3))));
        assertFalse(GearPredicate.test(requirement,context));
    }

    @Test void twoDifferentPickaxesRequireDistinctStacks() {
        var requirement=JsonParser.parseString("""
            {"items":[
              {"slot":"inventory","item":"minecraft:netherite_pickaxe","enchantments":{"minecraft:fortune":3}},
              {"slot":"inventory","item":"minecraft:netherite_pickaxe","enchantments":{"minecraft:silk_touch":1}}
            ]}
            """);
        var gear=new HashMap<String,Object>();
        gear.put("inventory",List.of(item("minecraft:netherite_pickaxe",Map.of("minecraft:fortune",3))));
        assertFalse(GearPredicate.test(requirement,Map.of("gear",gear)));
        gear.put("inventory",List.of(item("minecraft:netherite_pickaxe",Map.of("minecraft:fortune",3)),
            item("minecraft:netherite_pickaxe",Map.of("minecraft:silk_touch",1))));
        assertTrue(GearPredicate.test(requirement,Map.of("gear",gear)));
    }
    @Test void invalidEnchantmentsAndSlotsFailClosed() {
        assertThrows(IllegalArgumentException.class,()->GearPredicate.validate(
            JsonParser.parseString("{\"items\":[{\"slot\":\"unknown\",\"item\":\"minecraft:stone\"}]}")));
        assertThrows(IllegalArgumentException.class,()->GearPredicate.validate(
            JsonParser.parseString("{\"items\":[{\"slot\":\"inventory\",\"item\":\"minecraft:stone\",\"enchantments\":{\"minecraft:protection\":0}}]}")));
    }
}
