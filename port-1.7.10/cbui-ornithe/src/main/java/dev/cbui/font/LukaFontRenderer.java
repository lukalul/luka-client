package dev.cbui.font;

import dev.cbui.Cbui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import dev.luka.compat.GlStateManager;
import dev.luka.compat.VertexBuffer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import dev.luka.compat.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LukaFontRenderer {
    private static final int COUNT=256; private final ResourceLocation source; private final float requestedSize;
    private final Glyph[] glyphs=new Glyph[COUNT]; private DynamicTexture texture; private int lineHeight=10;
    /** Raster multiplier the atlas was built for: glyphs are rasterised at size x built so text stays sharp when the GUI scale magnifies it. */
    private int built; private boolean failed;
    private int cacheW,cacheH,cacheGui,cacheM=2;
    private final Map<String,Integer> widthCache=new HashMap<String,Integer>(512);
    public LukaFontRenderer(ResourceLocation source,float size){this.source=source;this.requestedSize=size;}
    private int rasterScale(){
        Minecraft mc=Minecraft.getMinecraft();int w=mc.displayWidth,h=mc.displayHeight,gui=mc.gameSettings.guiScale;
        if(w!=cacheW||h!=cacheH||gui!=cacheGui){cacheW=w;cacheH=h;cacheGui=gui;cacheM=Math.max(2,Math.min(4,dev.luka.compat.Compat.scaled(mc).getScaleFactor()));}
        return cacheM;
    }
    private void ensure(){
        if(failed)return;int m=rasterScale();if(texture!=null&&m==built)return;
        try{release();build(m);}catch(Throwable error){failed=true;Cbui.reportOnce("font atlas "+source,error);}
    }
    /** Drops the atlas and everything compiled from it (display lists hold atlas coordinates). */
    private void release(){
        if(texture!=null){GlStateManager.deleteTexture(texture.getGlTextureId());texture=null;}
        for(ListEntry e:lists.values())GL11.glDeleteLists(e.id,1);
        lists.clear();widthCache.clear();lastText=null;
    }
    private void build(int m)throws Exception{
        int atlas=m<=2?512:1024;
        Font font;try(InputStream in=Minecraft.getMinecraft().getResourceManager().getResource(source).getInputStream()){font=Font.createFont(Font.TRUETYPE_FONT,in).deriveFont(requestedSize*m);}
        BufferedImage image=new BufferedImage(atlas,atlas,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        // transparent white, not transparent black: bilinear filtering then never blends a dark fringe into glyph edges
        g.setComposite(AlphaComposite.Src);g.setColor(new Color(255,255,255,0));g.fillRect(0,0,atlas,atlas);g.setComposite(AlphaComposite.SrcOver);
        g.setFont(font);g.setColor(Color.WHITE);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics metrics=g.getFontMetrics();lineHeight=(int)Math.ceil(metrics.getHeight()/(float)m);AtlasPacker packer=new AtlasPacker(atlas,atlas,1);
        for(int i=0;i<COUNT;i++){char c=(char)i;int advance=Math.max(1,metrics.charWidth(c)),w=Math.max(1,advance+2),h=metrics.getHeight();int[] p=packer.place(w,h);if(p==null)throw new IllegalStateException("Glyph atlas full at "+i);g.drawString(String.valueOf(c),p[0]+1,p[1]+metrics.getAscent());glyphs[i]=new Glyph(p[0],p[1],w,h,advance,atlas,m);}
        g.dispose();texture=new DynamicTexture(image);built=m;
        GlStateManager.bindTexture(texture.getGlTextureId());GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
    }
    public float drawString(String text,float x,float y,int color){return draw(text,x,y,color,false);}
    public float drawStringWithShadow(String text,float x,float y,int color){return draw(text,x,y,color,true);}
    public float drawCentered(String text,float center,float y,int color){return drawString(text,center-getStringWidth(text)/2f,y,color);}
    private float draw(String text,float x,float y,int baseColor,boolean shadow){
        if(text==null||text.isEmpty())return x;ensure();if(texture==null)return x;
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.enableTexture2D();GlStateManager.bindTexture(texture.getGlTextureId());
        if(listsOk){
            try{
                // HUD text barely changes between frames: the quads are compiled once into a display list (colours baked in) and replayed at (x, y)
                String key=(shadow?"s":"n")+baseColor+'|'+text;ListEntry e=lists.get(key);
                if(e==null){e=compile(text,baseColor,shadow);lists.put(key,e);}
                GlStateManager.pushMatrix();GlStateManager.translate(x,y,0);GL11.glCallList(e.id);GlStateManager.popMatrix();
                GlStateManager.color(1,1,1,1);return x+e.width;
            }catch(Throwable error){listsOk=false;Cbui.reportOnce("font display lists (switched to immediate drawing)",error);}
        }
        return drawImmediate(text,x,y,baseColor,shadow);
    }
    private static final class ListEntry{final int id;final float width;ListEntry(int id,float width){this.id=id;this.width=width;}}
    private boolean listsOk=true;
    private final LinkedHashMap<String,ListEntry> lists=new LinkedHashMap<String,ListEntry>(256,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,ListEntry> e){if(size()>192){GL11.glDeleteLists(e.getValue().id,1);return true;}return false;}};
    private ListEntry compile(String text,int baseColor,boolean shadow){
        int id=GL11.glGenLists(1);float end;
        GL11.glNewList(id,GL11.GL_COMPILE);
        try{
            VertexBuffer wr=dev.luka.compat.VertexBuffer.INSTANCE;wr.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
            if(shadow)emit(wr,text,1,1,(baseColor&0xFF000000)|((baseColor&0xFCFCFC)>>2),true);
            end=emit(wr,text,0,0,baseColor,false);
            Tessellator.instance.draw();
        }finally{GL11.glEndList();}
        return new ListEntry(id,end);
    }
    private float drawImmediate(String text,float x,float y,int baseColor,boolean shadow){
        VertexBuffer wr=dev.luka.compat.VertexBuffer.INSTANCE;wr.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
        // shadow and text share one batch (one draw call): the shadow pass is emitted first so the text lands on top
        if(shadow)emit(wr,text,x+1,y+1,(baseColor&0xFF000000)|((baseColor&0xFCFCFC)>>2),true);
        float end=emit(wr,text,x,y,baseColor,false);
        Tessellator.instance.draw();GlStateManager.color(1,1,1,1);return end;
    }
    private float emit(VertexBuffer wr,String text,float x,float y,int baseColor,boolean flat){
        float cursor=x;int color=baseColor;int a=color>>>24&255;if(a==0)a=255;int r=color>>>16&255,gr=color>>>8&255,b=color&255;
        for(int i=0,n=text.length();i<n;i++){
            char c=text.charAt(i);if(c=='§'&&i+1<n){i++;if(!flat){color=formatColor(text.charAt(i),baseColor);a=color>>>24&255;if(a==0)a=255;r=color>>>16&255;gr=color>>>8&255;b=color&255;}continue;}if(c>=COUNT)c='?';Glyph q=glyphs[c];if(q==null)continue;
            float x2=cursor+q.w2,y2=y+q.h2;
            wr.pos(cursor,y,0).tex(q.u,q.v).color(r,gr,b,a).endVertex();wr.pos(cursor,y2,0).tex(q.u,q.v2).color(r,gr,b,a).endVertex();wr.pos(x2,y2,0).tex(q.u2,q.v2).color(r,gr,b,a).endVertex();wr.pos(x2,y,0).tex(q.u2,q.v).color(r,gr,b,a).endVertex();cursor+=q.adv2;
        }
        return cursor;
    }
    private String lastText;private int lastWidth;
    public int getStringWidth(String text){if(text==null)return 0;if(text==lastText)return lastWidth;int w=measure(text);lastText=text;lastWidth=w;return w;}
    private int measure(String text){Integer cached=widthCache.get(text);if(cached!=null)return cached;ensure();int width=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='\u00a7'&&i+1<text.length()){i++;continue;}if(c>=COUNT)c='?';Glyph g=glyphs[c];if(g!=null)width+=g.advance;}width=(int)Math.ceil(width/(float)built);if(widthCache.size()>1024)widthCache.clear();widthCache.put(text,width);return width;}
    public int lineHeight(){ensure();return lineHeight;}
    private static int formatColor(char c,int fallback){int i="0123456789abcdef".indexOf(Character.toLowerCase(c));if(i<0)return c=='r'?fallback:fallback;int dark=(i>>3)*85,r=(i>>2&1)*170+dark,g=(i>>1&1)*170+dark,b=(i&1)*170+dark;if(i==6)r+=85;return (fallback&0xFF000000)|(r<<16)|(g<<8)|b;}
    private static final class Glyph{final int x,y,w,h,advance;final float u,v,u2,v2,w2,h2,adv2;Glyph(int x,int y,int w,int h,int advance,int atlas,int m){this.x=x;this.y=y;this.w=w;this.h=h;this.advance=advance;u=x/(float)atlas;v=y/(float)atlas;u2=(x+w)/(float)atlas;v2=(y+h)/(float)atlas;w2=w/(float)m;h2=h/(float)m;adv2=advance/(float)m;}}
}
