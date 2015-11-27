package com.marcouberti.f35watchface;

import android.content.Context;

import java.util.HashMap;

public class GradientsUtils {
    static HashMap<String, Integer> map = new HashMap<>();
    static {
        map.put("Color 1",0);
        map.put("Color 2",1);
        map.put("Color 3",2);
        map.put("Color 4",3);
        map.put("Color 5",4);
        map.put("Color 6",5);
        map.put("Color 7",6);
        map.put("Color 8",7);
        map.put("Color 9",8);
        map.put("Color 10",9);
        map.put("Color 11",10);
        map.put("Color 12",11);
        map.put("Color 13",12);
        map.put("Color 14",13);
        map.put("Color 15",14);
        map.put("Color 16",15);
        map.put("Color 17",16);
        map.put("Color 18",17);
        map.put("Color 19",18);
        map.put("Color 20",19);
        map.put("Color 21",20);
        map.put("Color 22",21);
        map.put("Color 23",22);
    }

    public static int getGradients(Context ctx, int colorID) {
        if (colorID == 0) {
            return ctx.getResources().getColor(R.color.col_1);
        }else if (colorID == 1) {
            return ctx.getResources().getColor(R.color.col_2);
        }
        else if (colorID == 2) {
            return ctx.getResources().getColor(R.color.col_3);
        }
        else if (colorID == 3) {
            return ctx.getResources().getColor(R.color.col_4);
        }
        else if (colorID == 4) {
            return ctx.getResources().getColor(R.color.col_5);
        }
        else if (colorID == 5) {
            return ctx.getResources().getColor(R.color.col_6);
        }
        else if (colorID == 6) {
            return ctx.getResources().getColor(R.color.col_7);
        }
        else if (colorID == 7) {
            return ctx.getResources().getColor(R.color.col_8);
        }
        else if (colorID ==8) {
            return ctx.getResources().getColor(R.color.col_9);
        }
        else if (colorID == 9) {
            return ctx.getResources().getColor(R.color.col_10);
        }
        else if (colorID == 10) {
            return ctx.getResources().getColor(R.color.col_11);
        }
        else if (colorID == 11) {
            return ctx.getResources().getColor(R.color.col_12);
        }
        else if (colorID == 12) {
            return ctx.getResources().getColor(R.color.col_13);
        }
        else if (colorID == 13) {
            return ctx.getResources().getColor(R.color.col_14);
        }
        else if (colorID == 14) {
            return ctx.getResources().getColor(R.color.col_15);
        }
        else if (colorID == 15) {
            return ctx.getResources().getColor(R.color.col_16);
        }
        else if (colorID == 16) {
            return ctx.getResources().getColor(R.color.col_17);
        }
        else if (colorID == 17) {
            return ctx.getResources().getColor(R.color.col_18);
        }
        else if (colorID == 18) {
            return ctx.getResources().getColor(R.color.col_19);
        }
        else if (colorID == 19) {
            return ctx.getResources().getColor(R.color.col_20);
        }
        else if (colorID == 20) {
            return ctx.getResources().getColor(R.color.col_21);
        }
        else if (colorID == 21) {
            return ctx.getResources().getColor(R.color.col_22);
        }
        else if (colorID == 22){
            return ctx.getResources().getColor(R.color.col_23);
        }else {
            return ctx.getResources().getColor(R.color.col_4);
        }
    }

    public static int getGradients(Context ctx, String colorName) {
        if(colorName == null || !map.containsKey(colorName)) return ctx.getResources().getColor(R.color.col_18);
        return getGradients(ctx, map.get(colorName));
    }

    public static int getColorID(String colorName) {
        return map.get(colorName);
    }
}
