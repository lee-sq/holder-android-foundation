//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.holderzone.hardware.cabinet.util;

import com.bjw.utils.FuncUtil;


public class FuncUtils extends FuncUtil {
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String CharArrToHex(char[] inCharArr, int crc) {
        StringBuilder strBuilder = new StringBuilder();
        int j = inCharArr.length;

        for (int i = 0; i < j; ++i) {
            strBuilder.append(Byte2Hex((byte) inCharArr[i]));
            strBuilder.append("");
        }

        strBuilder.append(String.format("%02x", crc >> 8).toUpperCase());
        strBuilder.append("");
        strBuilder.append(String.format("%02x", crc & 255).toUpperCase());
        strBuilder.append("");
        return strBuilder.toString();
    }


    public static int isOdd(int num) {
        return num & 1;
    }

    public static int HexToInt(String inHex) {
        return Integer.parseInt(inHex, 16);
    }

    public static byte HexToByte(String inHex) {
        return (byte) Integer.parseInt(inHex, 16);
    }

    public static String Byte2Hex(byte b) {
        char[] hexChars = new char[2];
        int v = b & 0xFF;
        hexChars[0] = HEX_ARRAY[v >>> 4];
        hexChars[1] = HEX_ARRAY[v & 0x0F];
        return new String(hexChars);
    }

    public static String ByteArrToHex(byte[] inBytArr) {
        if (inBytArr == null || inBytArr.length == 0) {
            return "";
        }
        int len = inBytArr.length;
        char[] hexChars = new char[len * 2];

        for (int i = 0; i < len; i++) {
            int v = inBytArr[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }

        return new String(hexChars);
    }

    public static byte[] HexToByteArr(String inHex) {
        int hexlen = inHex.length();
        byte[] result;
        if (isOdd(hexlen) == 1) {
            ++hexlen;
            result = new byte[hexlen / 2];
            inHex = "0" + inHex;
        } else {
            result = new byte[hexlen / 2];
        }

        int j = 0;

        for (int i = 0; i < hexlen; i += 2) {
            result[j] = HexToByte(inHex.substring(i, i + 2));
            ++j;
        }

        return result;
    }
}
