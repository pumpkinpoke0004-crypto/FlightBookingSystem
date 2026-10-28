package com.flightbooking;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Map;

/** Minimal JSON writer for the small demo API; it deliberately has no external dependencies. */
public final class Json {
    private Json() {}
    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?,?> map) {
            StringBuilder out = new StringBuilder("{"); Iterator<? extends Map.Entry<?,?>> it = map.entrySet().iterator();
            while (it.hasNext()) { Map.Entry<?,?> e = it.next(); out.append(quote(String.valueOf(e.getKey()))).append(':').append(stringify(e.getValue())); if(it.hasNext())out.append(','); }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder out = new StringBuilder("["); Iterator<?> it=iterable.iterator(); while(it.hasNext()){out.append(stringify(it.next()));if(it.hasNext())out.append(',');}return out.append(']').toString();
        }
        if (value.getClass().isArray()) { StringBuilder out=new StringBuilder("[");for(int i=0;i<Array.getLength(value);i++){if(i>0)out.append(',');out.append(stringify(Array.get(value,i)));}return out.append(']').toString(); }
        return quote(value.toString());
    }
    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i=0;i<text.length();i++) { char c=text.charAt(i); switch(c) { case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f"); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t"); default -> { if(c<0x20) out.append(String.format("\\u%04x",(int)c)); else out.append(c); } } }
        return out.append('"').toString();
    }
}
