package dev.fastpacks.gui;

import dev.fastpacks.FastPacks;
import dev.fastpacks.index.*;
import java.io.File;
import java.nio.file.Files;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;

final class GuiMovePack extends GuiScreen {
    private final GuiScreen parent;private final PackIndex index;private final PackRecord pack;private GuiTextField path;private String error="";
    GuiMovePack(GuiScreen parent,PackIndex index,PackRecord pack){this.parent=parent;this.index=index;this.pack=pack;}
    @Override public void initGui(){try{Keyboard.enableRepeatEvents(true);path=new GuiTextField(0,fontRendererObj,width/2-120,height/2-10,240,20);path.setMaxStringLength(192);path.setFocused(true);buttonList.add(new GuiButton(0,width/2-102,height/2+18,100,20,"Move"));buttonList.add(new GuiButton(1,width/2+2,height/2+18,100,20,"Cancel"));}catch(Throwable t){fail("move dialog init",t);}}
    @Override protected void actionPerformed(GuiButton b){try{if(b.id==1)mc.displayGuiScreen(parent);else if(b.id==0)move();}catch(Throwable t){fail("move dialog button",t);}}
    private void move(){String rel=path.getText().trim().replace('\\','/');if(dev.fastpacks.pack.ExtraPacks.isExtraPath(pack.relativePath)){error="Packs in .minecraft are read only";return;}if(dev.fastpacks.pack.ExtraPacks.isExtraPath(rel)){error="Choose a folder in resourcepacks";return;}if(rel.startsWith("/")||rel.contains("..")||(!rel.isEmpty()&&(rel.split("/").length>3||rel.matches(".*[:*?\"<>|].*")))){error="Enter an existing folder (max 3 levels), or blank for root";return;}try{File root=mc.getResourcePackRepository().getDirResourcepacks();File dir=rel.isEmpty()?root:new File(root,rel);if(!dir.isDirectory()){error="Folder does not exist";return;}File target=new File(dir,pack.file.getName());if(target.exists()){error="Destination already contains that name";return;}Files.move(pack.file.toPath(),target.toPath());index.refresh(true);mc.displayGuiScreen(parent);}catch(Throwable t){FastPacks.reportOnce("move pack",t);error="Move failed; nothing was deleted";}}
    @Override protected void keyTyped(char c,int k){try{if(k==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}if(k==Keyboard.KEY_RETURN){move();return;}path.textboxKeyTyped(c,k);}catch(Throwable t){fail("move dialog keyboard",t);}}
    @Override protected void mouseClicked(int x,int y,int b){try{super.mouseClicked(x,y,b);path.mouseClicked(x,y,b);}catch(Throwable t){fail("move dialog mouse",t);}}
    @Override public void drawScreen(int x,int y,float p){try{drawDefaultBackground();drawCenteredString(fontRendererObj,"Move "+pack.name+" to folder (blank = root)",width/2,height/2-35,0xffffff);path.drawTextBox();drawCenteredString(fontRendererObj,error,width/2,height/2+43,0xff5555);super.drawScreen(x,y,p);}catch(Throwable t){fail("move dialog draw",t);}}
    private void fail(String where,Throwable t){try{FastPacks.openVanilla(mc,parent,where,t);}catch(Throwable ignored){FastPacks.reportOnce(where,t);}}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
}
