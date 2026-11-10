package dev.cbui.hud.module;

import dev.cbui.hud.*;

public final class ToggleSprintModule extends AbstractHudModule {
    private boolean toggled,lastKey; private String label="[Sprinting (Key Held)]";
    public ToggleSprintModule(){super("Toggle sprint",false,Anchor.BOTTOM_CENTER,0,-50,110,11);}
    @Override public void tick(){
        boolean key=mc.gameSettings.keyBindSprint.getIsKeyPressed();if(key&&!lastKey)toggled=!toggled;lastKey=key;
        if(mc.thePlayer!=null&&toggled&&mc.thePlayer.moveForward>0&&!mc.thePlayer.isSneaking())mc.thePlayer.setSprinting(true);
        label=toggled?"[Sprinting (Toggled)]":"[Sprinting (Key Held)]";
    }
    @Override protected void draw(float partialTicks,boolean editor){centered(editor?"[Sprinting (Toggled)]":label,0);}
}
