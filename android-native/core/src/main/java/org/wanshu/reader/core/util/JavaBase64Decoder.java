package org.wanshu.reader.core.util;

import java.util.Base64;

public class JavaBase64Decoder implements Base64Decoder {

    @Override
    public byte[] decode(String base64) {
        if (base64 == null) {
            return new byte[0];
        }
        String clean = base64.replaceAll("\\s+", "");
        return Base64.getDecoder().decode(clean);
    }
}
