package dev.cbui.hud.module;

import dev.cbui.hud.*;

public final class ToggleSprintModule extends AbstractHudModule {
    private boolean toggled,lastKey; private String label="[Sprinting (Key Held)]";
    public ToggleSprintModule(){super("Toggle sprint",false,Anchor.BOTTOM_CENTER,0,-50,110,11);}
    @Override public void tick(){
        // the physical key toggles; while toggled the sprint key is held for the player and the game sprints as in vanilla
        boolean key=SprintKey.physicallyDown(mc);if(key&&!lastKey)toggled=!toggled;lastKey=key;
        if(mc.thePlayer!=null&&toggled&&mc.currentScreen==null)SprintKey.hold(mc,true);
        label=toggled?"[Sprinting (Toggled)]":"[Sprinting (Key Held)]";
    }
    @Override protected void draw(float partialTicks,boolean editor){centered(editor?"[Sprinting (Toggled)]":label,0);}
}
