package dev.cbui.hud;

public final class LayoutMath {
    public static float[] bounds(Anchor anchor,float offsetX,float offsetY,float width,float height,float scale,int screenWidth,int screenHeight) {
        float w=width*scale,h=height*scale,x,y;
        switch(anchor) {
            case TOP_CENTER: x=(screenWidth-w)/2; y=2; break;
            case TOP_RIGHT: x=screenWidth-w-2; y=2; break;
            case CENTER_LEFT: x=2; y=(screenHeight-h)/2; break;
            case CENTER: x=(screenWidth-w)/2; y=(screenHeight-h)/2; break;
            case CENTER_RIGHT: x=screenWidth-w-2; y=(screenHeight-h)/2; break;
            case BOTTOM_LEFT: x=2; y=screenHeight-h-2; break;
            case BOTTOM_CENTER: x=(screenWidth-w)/2; y=screenHeight-h-2; break;
            case BOTTOM_RIGHT: x=screenWidth-w-2; y=screenHeight-h-2; break;
            default: x=2; y=2;
        }
        x=clamp(x+offsetX,0,Math.max(0,screenWidth-w)); y=clamp(y+offsetY,0,Math.max(0,screenHeight-h));
        return new float[]{x,y,w,h};
    }
    /**
     * HUD editor snapping for a dragged box {x,y,w,h}. Each axis independently snaps the box's start, centre or end to the
     * nearest line within {@code threshold}: the screen margins and centre, and for every other box its start, centre and end
     * plus flush-against-it (so boxes line up or stack edge to edge). Then the box is kept {@code margin} units inside the
     * screen. Returns the {dx,dy} correction.
     */
    public static float[] snap(float[] box,Iterable<float[]> others,float screenWidth,float screenHeight,float margin,float threshold) {
        java.util.List<float[]> xs=new java.util.ArrayList<>(),ys=new java.util.ArrayList<>();
        // a line is {position, kind}: kind 0 aligns any of start/centre/end, 1 only the start (flush after another box), 2 only the end (flush before)
        xs.add(new float[]{margin,0});xs.add(new float[]{screenWidth/2,0});xs.add(new float[]{screenWidth-margin,0});
        ys.add(new float[]{margin,0});ys.add(new float[]{screenHeight/2,0});ys.add(new float[]{screenHeight-margin,0});
        for(float[] o:others){
            xs.add(new float[]{o[0],0});xs.add(new float[]{o[0]+o[2]/2,0});xs.add(new float[]{o[0]+o[2],0});
            xs.add(new float[]{o[0]+o[2],1});xs.add(new float[]{o[0],2});
            ys.add(new float[]{o[1],0});ys.add(new float[]{o[1]+o[3]/2,0});ys.add(new float[]{o[1]+o[3],0});
            ys.add(new float[]{o[1]+o[3],1});ys.add(new float[]{o[1],2});
        }
        float dx=best(box[0],box[2],xs,threshold),dy=best(box[1],box[3],ys,threshold);
        float x=box[0]+dx,y=box[1]+dy;
        if(x<margin)dx+=margin-x; else if(x+box[2]>screenWidth-margin)dx+=screenWidth-margin-x-box[2];
        if(y<margin)dy+=margin-y; else if(y+box[3]>screenHeight-margin)dy+=screenHeight-margin-y-box[3];
        return new float[]{dx,dy};
    }
    private static float best(float start,float size,java.util.List<float[]> lines,float threshold){
        float best=0,bestAbs=threshold+0.0001f;
        for(float[] line:lines){
            int kind=(int)line[1];
            float[] own=kind==0?new float[]{start,start+size/2,start+size}:kind==1?new float[]{start}:new float[]{start+size};
            for(float v:own){
                float d=line[0]-v;
                if(Math.abs(d)<bestAbs){bestAbs=Math.abs(d);best=d;}
            }
        }
        return best;
    }
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private LayoutMath(){ }
}
