package io.anuke.arc.backends.gwt.preloader;

import com.google.gwt.typedarrays.shared.Int8Array;

import java.io.InputStream;

public final class Blob{

    private final Int8Array data;

    public Blob(Int8Array data){
        this.data = data;
    }

    public int length(){
        return data.length();
    }

    public byte get(int i){
        return data.get(i);
    }

    public InputStream read(){
        return new InputStream(){

            int pos;

            @Override
            public int read(){
                if(pos == length()) return -1;
                return get(pos++) & 0xff;
            }

            @Override
            public int available(){
                return length() - pos;
            }
        };
    }

    public String toBase64(){
        int length = data.length();
        String base64code = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder encoded = new StringBuilder(length * 4 / 3 + 2);
        for(int i = 0; i < length; i += 3){
            if(length - i >= 3){
                int j = ((data.get(i) & 0xff) << 16) + ((data.get(i + 1) & 0xff) << 8) + (data.get(i + 2) & 0xff);
                encoded.append(base64code.charAt((j >> 18) & 0x3f));
                encoded.append(base64code.charAt((j >> 12) & 0x3f));
                encoded.append(base64code.charAt((j >> 6) & 0x3f));
                encoded.append(base64code.charAt(j & 0x3f));
            }else if(length - i >= 2){
                int j = ((data.get(i) & 0xff) << 16) + ((data.get(i + 1) & 0xff) << 8);
                encoded.append(base64code.charAt((j >> 18) & 0x3f));
                encoded.append(base64code.charAt((j >> 12) & 0x3f));
                encoded.append(base64code.charAt((j >> 6) & 0x3f));
                encoded.append("=");
            }else{
                int j = ((data.get(i) & 0xff) << 16);
                encoded.append(base64code.charAt((j >> 18) & 0x3f));
                encoded.append(base64code.charAt((j >> 12) & 0x3f));
                encoded.append("==");
            }
        }
        return encoded.toString();
    }

}
