package dev.cbui.gui;

/** Minecraft-free panel math shared with the Task 3 plain-Java checks. */
public final class ModsLayout {
    public final int left, top, width, height, header, listWidth, gap, contentLeft, contentWidth, bodyTop, bodyHeight;
    private ModsLayout(int left,int top,int width,int height,int header,int listWidth,int gap){this.left=left;this.top=top;this.width=width;this.height=height;this.header=header;this.listWidth=listWidth;this.gap=gap;this.contentLeft=left+listWidth+gap;this.contentWidth=width-listWidth-gap;this.bodyTop=top+header;this.bodyHeight=height-header;}
    public static ModsLayout fit(int screenWidth,int screenHeight){int margin=screenWidth<500?5:12;int w=Math.max(180,screenWidth-margin*2),h=Math.max(100,screenHeight-margin*2);int header=Math.min(36,Math.max(25,h/5));int list=Math.min(190,Math.max(76,w*29/100));int gap=Math.min(8,Math.max(3,w/100));if(list+gap+90>w)list=Math.max(70,w-90-gap);return new ModsLayout((screenWidth-w)/2,(screenHeight-h)/2,w,h,header,list,gap);}
    public boolean valid(){return left>=0&&top>=0&&width>0&&height>0&&contentLeft>=left+listWidth&&contentLeft+contentWidth==left+width&&bodyTop+bodyHeight==top+height&&contentWidth>=80;}
    private ModsLayout(){throw new AssertionError();}
}
