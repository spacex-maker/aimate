package com.openforge.aimate.proxy;

import java.util.List;

public record ProxyEmbedRequest(
        Object input,
        String model
) {
    @SuppressWarnings("unchecked")
    public List<String> texts() {
        if (input == null) return List.of();
        if (input instanceof String s) return List.of(s);
        if (input instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(input));
    }
}
