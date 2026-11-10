package dev.fastpacks.gui;

import dev.fastpacks.FastPacks;
import dev.fastpacks.index.PackIndex;
import java.io.File;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;

final class GuiFolderName extends GuiScreen {
    private final GuiScreen parent; private final PackIndex index; private final String base; private GuiTextField name; private String error="";
    GuiFolderName(GuiScreen parent,PackIndex index,String base){this.parent=parent;this.index=index;this.base=base;}
    @Override public void initGui(){try{Keyboard.enableRepeatEvents(true);name=new GuiTextField(fontRendererObj,width/2-100,height/2-10,200,20);name.setFocused(true);buttonList.add(new GuiButton(0,width/2-102,height/2+18,100,20,"Create"));buttonList.add(new GuiButton(1,width/2+2,height/2+18,100,20,"Cancel"));}catch(Throwable t){fail("folder dialog init",t);}}
    @Override protected void actionPerformed(GuiButton b){try{if(b.id==1){mc.displayGuiScreen(parent);return;}if(b.id==0)create();}catch(Throwable t){fail("folder dialog button",t);}}
    private void create(){String n=name.getText().trim();if(!valid(n)){error="Use 1-64 normal filename characters";return;}if(dev.fastpacks.pack.ExtraPacks.isExtraPath(base)){error="The .minecraft folder is read only";return;}if(!base.isEmpty()&&base.split("/").length>=3){error="Folders are limited to 3 levels";return;}try{File root=mc.getResourcePackRepository().getDirResourcepacks();File f=new File(base.isEmpty()?root:new File(root,base),n);if(f.exists()){error="That name already exists";return;}if(!f.mkdir()){error="Could not create folder";return;}index.refresh(true);mc.displayGuiScreen(parent);}catch(Throwable t){FastPacks.reportOnce("create folder",t);error="Could not create folder";}}
    private static boolean valid(String n){return !n.isEmpty()&&n.length()<=64&&!n.equals(".")&&!n.equals("..")&&!n.matches(".*[\\\\/:*?\"<>|].*");}
    @Override protected void keyTyped(char c,int k){try{if(k==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}if(k==Keyboard.KEY_RETURN){create();return;}name.textboxKeyTyped(c,k);}catch(Throwable t){fail("folder dialog keyboard",t);}}
    @Override protected void mouseClicked(int x,int y,int b){try{super.mouseClicked(x,y,b);name.mouseClicked(x,y,b);}catch(Throwable t){fail("folder dialog mouse",t);}}
    @Override public void drawScreen(int x,int y,float p){try{drawDefaultBackground();drawCenteredString(fontRendererObj,"Create folder in /"+base,width/2,height/2-35,0xffffff);name.drawTextBox();drawCenteredString(fontRendererObj,error,width/2,height/2+43,0xff5555);super.drawScreen(x,y,p);}catch(Throwable t){fail("folder dialog draw",t);}}
    private void fail(String where,Throwable t){try{FastPacks.openVanilla(mc,parent,where,t);}catch(Throwable ignored){FastPacks.reportOnce(where,t);}}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
}
