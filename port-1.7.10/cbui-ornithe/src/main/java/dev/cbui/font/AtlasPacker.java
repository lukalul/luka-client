package dev.cbui.font;

public final class AtlasPacker {
    private final int width,height,padding; private int x,y,rowHeight;
    public AtlasPacker(int width,int height,int padding){this.width=width;this.height=height;this.padding=padding;}
    public int[] place(int itemWidth,int itemHeight){
        if(itemWidth+padding*2>width||itemHeight+padding*2>height)return null;
        if(x+itemWidth+padding*2>width){x=0;y+=rowHeight;rowHeight=0;}
        if(y+itemHeight+padding*2>height)return null;
        int[] result={x+padding,y+padding};x+=itemWidth+padding*2;rowHeight=Math.max(rowHeight,itemHeight+padding*2);return result;
    }
}
