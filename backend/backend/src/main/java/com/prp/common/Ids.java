package com.prp.common;

import java.util.UUID;

/** Readable, prefixed IDs such as PAY-3F9A1C2B. */
public final class Ids {
    private Ids() {}

    public static String next(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
