package com.holderzone.common.ext

fun <T, K> List<T>.toMapBy(keySelector: (T) -> K): Map<K, T> = associateBy(keySelector)

fun <T, K, V> List<T>.toMap(keySelector: (T) -> K, valueSelector: (T) -> V): Map<K, V> = associateBy(keySelector, valueSelector)

fun <T, K, V> List<T>.toMapMerge(
    keySelector: (T) -> K,
    valueSelector: (T) -> V,
    merge: (V, V) -> V
): Map<K, V> {
    val map = LinkedHashMap<K, V>(size)
    for (item in this) {
        val k = keySelector(item)
        val v = valueSelector(item)
        val existing = map[k]
        map[k] = if (existing == null) v else merge(existing, v)
    }
    return map
}