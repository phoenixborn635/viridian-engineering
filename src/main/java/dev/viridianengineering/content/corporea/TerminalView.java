package dev.viridianengineering.content.corporea;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Server-side terminal view (spec 3.6: search and sort run on the server; clients receive
 * 64-entry pages). Pure logic with no world access, so it is easy to test.
 */
public final class TerminalView {

    public static final int PAGE_SIZE = 64;

    private TerminalView() {}

    /** One distinct item (item + exact components) and its total count across the network. */
    public record Entry(ItemStack template, long count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Entry::template,
                ByteBufCodecs.VAR_LONG, Entry::count,
                Entry::new);
    }

    public enum Sort {
        NAME, COUNT, MOD;

        public Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Merges stacks of the same item and components into one entry with a long count. */
    public static List<Entry> aggregate(List<ItemStack> stacks) {
        Map<Key, long[]> totals = new LinkedHashMap<>();
        Map<Key, ItemStack> templates = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            Key key = new Key(stack);
            totals.computeIfAbsent(key, k -> new long[1])[0] += stack.getCount();
            templates.putIfAbsent(key, stack.copyWithCount(1));
        }
        List<Entry> out = new ArrayList<>(totals.size());
        totals.forEach((k, v) -> out.add(new Entry(templates.get(k), v[0])));
        return out;
    }

    /**
     * Search: case-insensitive name match; a term starting with {@code @} matches the mod id
     * (e.g. {@code @botania}). Several space-separated terms must all match.
     */
    public static List<Entry> filterAndSort(List<Entry> entries, String search, Sort sort) {
        String[] terms = search == null ? new String[0] : search.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (matches(e, terms)) out.add(e);
        }
        Comparator<Entry> byName = Comparator.comparing(e -> e.template().getHoverName().getString().toLowerCase(Locale.ROOT));
        Comparator<Entry> cmp = switch (sort) {
            case NAME -> byName;
            case COUNT -> Comparator.comparingLong(Entry::count).reversed().thenComparing(byName);
            case MOD -> Comparator.<Entry, String>comparing(e -> namespace(e.template())).thenComparing(byName);
        };
        out.sort(cmp);
        return out;
    }

    public static int pageCount(int entries) {
        return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public static List<Entry> page(List<Entry> view, int page) {
        int from = Math.min(view.size(), page * PAGE_SIZE);
        int to = Math.min(view.size(), from + PAGE_SIZE);
        return List.copyOf(view.subList(from, to));
    }

    private static boolean matches(Entry e, String[] terms) {
        if (terms.length == 0) return true;
        String name = e.template().getHoverName().getString().toLowerCase(Locale.ROOT);
        String mod = namespace(e.template());
        for (String t : terms) {
            if (t.isEmpty()) continue;
            if (t.startsWith("@")) {
                if (!mod.contains(t.substring(1))) return false;
            } else if (!name.contains(t)) {
                return false;
            }
        }
        return true;
    }

    private static String namespace(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    /** Compact count label: 999, 1.2k, 45k, 3.4M. */
    public static String formatCount(long count) {
        if (count < 1_000) return Long.toString(count);
        if (count < 10_000) return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
        if (count < 1_000_000) return (count / 1000) + "k";
        if (count < 10_000_000) return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
        return (count / 1_000_000) + "M";
    }

    /** Hash key that treats stacks with the same item and components as equal. */
    private static final class Key {
        private final ItemStack stack;
        private final int hash;

        Key(ItemStack stack) {
            this.stack = stack;
            this.hash = ItemStack.hashItemAndComponents(stack);
        }

        @Override public int hashCode() { return hash; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && ItemStack.isSameItemSameComponents(stack, k.stack);
        }
    }

    static boolean sameEntries(List<Entry> a, List<Entry> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Entry x = a.get(i), y = b.get(i);
            if (x.count() != y.count() || !ItemStack.isSameItemSameComponents(x.template(), y.template())) return false;
        }
        return Objects.equals(a.size(), b.size());
    }
}
