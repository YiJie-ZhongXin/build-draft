package dev.builddraft.model;

import java.util.*;

/** One material group, an independent shape facet, and stable material-family ordering. */
public final class BlockCategoryRules {
    public enum Material {
        ALL("全部"), WOOD("木材"), STONE("石材"), COLORED("彩色建材"), GLASS("玻璃"), NATURAL("自然"), DECOR("装饰"), OTHER("其他");
        public final String label;
        Material(String label) { this.label = label; }
    }
    public enum Shape {
        ALL("全部形状"), FULL("完整方块"), STAIRS("楼梯"), SLABS("台阶"), FENCES("墙/围栏"), DOORS("门/活板门"), OTHER("其他形状");
        public final String label;
        Shape(String label) { this.label = label; }
    }
    public enum Source { ALL, VANILLA, MODDED }
    public enum Trait { WOOD, STONE, COLORED, GLASS, NATURAL, DECOR, FULL, STAIRS, SLABS, FENCES, DOORS, SPECIAL }
    public record Membership(Material material, Shape shape, String family, int variant, boolean special) { }
    private static final List<String> WOODS = List.of("dark_oak", "mangrove", "crimson", "warped", "bamboo", "cherry", "spruce", "birch", "jungle", "acacia", "oak");
    private static final List<String> STONES = List.of("red_sandstone", "sandstone", "end_stone", "cobblestone", "deepslate", "blackstone", "prismarine", "andesite", "diorite", "granite", "calcite", "basalt", "quartz", "purpur", "tuff", "netherrack", "nether_brick", "mud_brick", "stone", "brick", "marble", "limestone", "slate", "travertine", "conglomerate");
    private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final Set<String> TECHNICAL = Set.of("air", "cave_air", "void_air", "barrier", "light", "structure_void", "structure_block", "jigsaw", "command_block", "chain_command_block", "repeating_command_block", "moving_piston", "piston_head");
    private static final List<String> SUFFIXES = List.of("_pressure_plate", "_fence_gate", "_hanging_sign", "_trapdoor", "_stairs", "_planks", "_hyphae", "_fence", "_button", "_slab", "_wall", "_door", "_wood", "_stem", "_sign", "_log");

    public static Membership classify(String id, Set<Trait> traits) {
        String path = path(id);
        Shape shape = shape(path, traits);
        Material material;
        if (traits.contains(Trait.DECOR) || words(path, "torch", "lantern", "lamp", "glowstone", "shroomlight", "froglight", "bookshelf", "chest", "barrel", "furnace", "hopper", "dispenser", "dropper", "sign", "bed", "redstone", "campfire", "anvil", "cauldron", "workbench", "crafting_table")) material = Material.DECOR;
        else if (traits.contains(Trait.GLASS) || words(path, "glass")) material = Material.GLASS;
        else if (traits.contains(Trait.COLORED) || words(path, "concrete", "terracotta", "wool", "carpet")) material = Material.COLORED;
        else if (traits.contains(Trait.NATURAL)) material = Material.NATURAL;
        else if (traits.contains(Trait.WOOD) || WOODS.stream().anyMatch(root -> word(path, root)) || path.endsWith("_planks") || path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae")) material = Material.WOOD;
        else if (traits.contains(Trait.STONE)) material = Material.STONE;
        else if (words(path, "leaves", "sapling", "flower", "mushroom", "grass", "fern", "vine", "roots", "dirt", "sand", "soil", "ore", "mud", "gravel", "snow", "ice", "coral")) material = Material.NATURAL;
        else if (WOODS.stream().anyMatch(root -> word(path, root))) material = Material.WOOD;
        else if (STONES.stream().anyMatch(root -> word(path, root))) material = Material.STONE;
        else if (shape == Shape.DOORS || words(path, "bars", "chain", "carpet", "candle")) material = Material.DECOR;
        else material = Material.OTHER;
        boolean special = traits.contains(Trait.SPECIAL) || id.startsWith("minecraft:") && TECHNICAL.contains(path);
        return new Membership(material, shape, family(path, material), variant(path, material, shape), special);
    }
    private static Shape shape(String path, Set<Trait> traits) {
        if (traits.contains(Trait.STAIRS) || path.endsWith("_stairs")) return Shape.STAIRS;
        if (traits.contains(Trait.SLABS) || path.endsWith("_slab")) return Shape.SLABS;
        if (traits.contains(Trait.FENCES) || path.endsWith("_fence") || path.endsWith("_fence_gate") || path.endsWith("_wall")) return Shape.FENCES;
        if (traits.contains(Trait.DOORS) || path.endsWith("_door") || path.endsWith("_trapdoor")) return Shape.DOORS;
        return traits.contains(Trait.FULL) ? Shape.FULL : Shape.OTHER;
    }
    private static String family(String path, Material material) {
        if (material == Material.COLORED) {
            if (words(path, "wool", "carpet")) return "wool";
            if (word(path, "terracotta")) return "terracotta";
            if (word(path, "concrete")) return "concrete";
        }
        if (material == Material.GLASS) return word(path, "stained") ? "stained_glass" : word(path, "tinted") ? "tinted_glass" : "glass";
        for (String root : material == Material.WOOD || material == Material.NATURAL ? WOODS : STONES) if (word(path, root)) return root;
        String result = path;
        for (String prefix : List.of("stripped_", "polished_", "smooth_", "chiseled_", "cracked_", "mossy_")) if (result.startsWith(prefix)) result = result.substring(prefix.length());
        for (String suffix : SUFFIXES) if (result.endsWith(suffix)) return result.substring(0, result.length() - suffix.length());
        return result;
    }
    private static int variant(String path, Material material, Shape shape) {
        int rank = switch (shape) { case FULL -> 0; case STAIRS -> 10; case SLABS -> 11; case FENCES -> 12; case DOORS -> 14; default -> 20; };
        if (material == Material.WOOD) {
            if (path.endsWith("_log") || path.endsWith("_stem")) rank = path.startsWith("stripped_") ? 1 : 0;
            else if (path.endsWith("_wood") || path.endsWith("_hyphae")) rank = path.startsWith("stripped_") ? 3 : 2;
            else if (path.endsWith("_planks")) rank = 4;
            else if (path.endsWith("_fence_gate")) rank = 13;
            else if (path.endsWith("_trapdoor")) rank = 15;
        }
        if (material == Material.COLORED || material == Material.GLASS) {
            int color = COLORS.size();
            for (int i = 0; i < COLORS.size(); i++) if (path.startsWith(COLORS.get(i) + "_")) { color = i; break; }
            return color * 32 + (path.endsWith("_pane") ? 1 : rank);
        }
        return rank * 16 + (path.startsWith("polished_") ? 1 : path.contains("brick") ? 2 : path.startsWith("chiseled_") ? 3 : path.startsWith("mossy_") ? 4 : path.startsWith("cracked_") ? 5 : 0);
    }
    public static Comparator<String> order(Map<String, Membership> memberships) {
        return Comparator.comparingInt((String id) -> memberships.get(id).material().ordinal())
            .thenComparing(id -> memberships.get(id).family()).thenComparingInt(id -> memberships.get(id).variant())
            .thenComparing(BlockCategoryRules::path).thenComparing(id -> id);
    }
    public static boolean matches(String id, String name, String query, Source source, Membership membership, Material material, Shape shape, boolean showSpecial) {
        boolean vanilla = id.startsWith("minecraft:");
        if (source == Source.VANILLA && !vanilla || source == Source.MODDED && vanilla) return false;
        if (!showSpecial && membership.special() || material != Material.ALL && material != membership.material() || shape != Shape.ALL && shape != membership.shape()) return false;
        String text = (id + " " + name).toLowerCase(Locale.ROOT);
        for (String token : query.strip().toLowerCase(Locale.ROOT).split("\\s+")) if (!text.contains(token)) return false;
        return true;
    }
    private static String path(String id) { return id.substring(id.indexOf(':') + 1).toLowerCase(Locale.ROOT).replace('/', '_'); }
    private static boolean words(String path, String... words) { return Arrays.stream(words).anyMatch(word -> word(path, word)); }
    private static boolean word(String path, String word) {
        return path.equals(word) || path.startsWith(word + "_") || path.endsWith("_" + word) || path.contains("_" + word + "_")
            || (word.endsWith("brick") && word(path, word + "s"));
    }
}
