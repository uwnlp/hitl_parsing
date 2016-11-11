package edu.uw.easysrl.qasrl.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableMap;

import java.util.stream.Collector;
import java.util.function.Function;
import static java.util.stream.Collector.Characteristics.UNORDERED;

/**
 * Convenience collectors for Guava immutable data structures.
 * This code was taken from https://gist.github.com/JakeWharton/9734167
 * Created by julianmichael on 3/17/2016.
 */
public final class GuavaCollectors {
    public static <T> Collector<T, ImmutableList.Builder<T>, ImmutableList<T>> toImmutableList() {
        return Collector.of(ImmutableList.Builder<T>::new,
                            ImmutableList.Builder<T>::add,
                            (l, r) -> l.addAll(r.build()),
                            ImmutableList.Builder<T>::build);
    }

    public static <T> Collector<T, ImmutableSet.Builder<T>, ImmutableSet<T>> toImmutableSet() {
        return Collector.of(ImmutableSet.Builder::new,
                            ImmutableSet.Builder::add,
                            (l, r) -> l.addAll(r.build()),
                            ImmutableSet.Builder<T>::build,
                            UNORDERED);
    }

    public static <T, K, V> Collector<T, ImmutableMap.Builder<K, V>, ImmutableMap<K, V>>
        toImmutableMap(Function<? super T, ? extends K> keyMapper,
                       Function<? super T, ? extends V> valueMapper) {
        return Collector.of(ImmutableMap.Builder<K, V>::new,
                            (r, t) -> r.put(keyMapper.apply(t), valueMapper.apply(t)),
                            (l, r) -> l.putAll(r.build()),
                            ImmutableMap.Builder<K, V>::build,
                            Collector.Characteristics.UNORDERED);
    }

    private GuavaCollectors() {
        throw new AssertionError("No instances.");
    }

    // public static <T> Stream<T> streamOf(ImmutableList<T> list) {
        
    // }
}
