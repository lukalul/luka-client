package dev.fastpacks.gui;

import dev.fastpacks.FastPacks;
import dev.fastpacks.index.*;
import dev.fastpacks.mixin.ResourcePackEntryInvoker;
import dev.fastpacks.preview.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Immediate-opening, virtualized resource-pack browser. Disk and ZIP reads stay on PackIndex workers. */
public final class GuiFastResourcePacks extends GuiScreen {
    private final GuiScreen parent;
    private final List<PackRecord> selected = new ArrayList<>();
    private final Map<String,ResourceLocation> icons = new HashMap<>();
    private final Map<String,CompletableFuture<Integer>> folderCounts = new HashMap<>();
    private final LinkedHashMap<String,PreviewEntry> previewCache = new LinkedHashMap<>(16,.75f,true);
    private PackIndex index; private GuiTextField search; private FastPacksLayout layout; private FastPacksConfig config;
    private String folder="", hoveredKey; private long hoverSince; 
    private int availableScroll,selectedScroll,availableChoice=-1,selectedChoice=-1,lastChoice=-999; private long lastClick;
    private volatile boolean applying; private boolean leaving;

    public GuiFastResourcePacks(GuiScreen parent){this.parent=parent;}
    @Override public void initGui(){try{
        Keyboard.enableRepeatEvents(true);buttonList.clear();layout=FastPacksLayout.calculate(width,height);
        if(config==null){config=FastPacksConfig.load(mc.mcDataDir);folder=config.lastFolder==null?"":config.lastFolder;}
        search=new GuiTextField(0,fontRendererObj,layout.search.left,layout.search.top,layout.search.width(),layout.search.height());search.setMaxStringLength(80);search.setFocused(true);
        int q=Math.max(38,(layout.nav.width()-12)/4),x=layout.nav.left;
        buttonList.add(new GuiButton(3,x,layout.nav.top,q,20,"Back"));x+=q+4;buttonList.add(new GuiButton(4,x,layout.nav.top,q,20,"New folder"));
        buttonList.add(new GuiButton(6,layout.nav.right-2*q-4,layout.nav.top,q,20,backgroundLabel()));buttonList.add(new GuiButton(7,layout.nav.right-q,layout.nav.top,q,20,previewLabel()));
        addButton(5,layout.move,"Move pack...");if(clearGlass()>=0)addButton(8,layout.background,clearGlassLabel());addButton(0,layout.done,"Done");addButton(1,layout.refresh,"Refresh");addButton(2,layout.cancel,"Cancel");
        File root=mc.getResourcePackRepository().getDirResourcepacks();if(index==null){index=new PackIndex(root);index.refresh(false).thenRun(this::restoreSelection);}else index.refresh(false);
    }catch(Throwable t){fail("screen initialization",t);}}
    private void addButton(int id,FastPacksLayout.Rect r,String text){buttonList.add(new GuiButton(id,r.left,r.top,r.width(),r.height(),text));}
    /** luka's Clear glass setting (Video Settings, Quality), shown here too; -1 when luka's settings are not there. */
    private static int clearGlass(){try{return Class.forName("dev.cbui.config.GlobalOptions").getField("clearGlass").getInt(null);}catch(Throwable t){return -1;}}
    private static String clearGlassLabel(){int m=clearGlass();return "Clear glass: "+(m==2?"All":m==1?"Regular":"Off");}
    private static void cycleClearGlass(){try{Class.forName("dev.cbui.config.PerfOptions").getMethod("setClearGlass",int.class).invoke(null,(clearGlass()+1)%3);}catch(Throwable t){FastPacks.reportOnce("clear glass",t);}}
    private String backgroundLabel(){return "Background: "+(config!=null&&config.transparentBackground?"Transparent":"Dirt");}
    private String previewLabel(){return "Previews: "+(config==null||config.previews?"On":"Off");}
    private void updateButton(int id,String text){for(Object o:buttonList){GuiButton b=(GuiButton)o;if(b.id==id){b.displayString=text;return;}}}
    private void restoreSelection(){try{List<String> saved=new ArrayList<>(mc.gameSettings.resourcePacks);Collections.reverse(saved);synchronized(selected){selVersion++;selected.clear();for(String name:saved)for(PackRecord p:index.snapshot())if((p.relativePath.equals(name)||p.name.equals(name))&&!selected.contains(p)){selected.add(p);break;}}}catch(Throwable t){FastPacks.reportOnce("selection restore",t);}}
    // availableRows() is called every frame; rebuilding + sorting it each time is wasted work, so it is cached until an input changes.
    private List<Object> rowsCache; private String rowsFolder, rowsQuery; private List<PackRecord> rowsSnapshot; private List<String> rowsFolders; private int rowsSelVersion; private long rowsBuilt; private volatile int selVersion;
    private List<Object> availableRows(){
        String q=search==null?"":search.getText().trim();long now=System.currentTimeMillis();
        // a non-empty query also matches descriptions, which load asynchronously, so those results are rebuilt twice a second
        if(rowsCache!=null&&rowsSelVersion==selVersion&&folder.equals(rowsFolder)&&q.equals(rowsQuery)&&index.snapshot()==rowsSnapshot&&index.folders()==rowsFolders&&(q.isEmpty()||now-rowsBuilt<500))return rowsCache;
        rowsSelVersion=selVersion;rowsFolder=folder;rowsQuery=q;rowsSnapshot=index.snapshot();rowsFolders=index.folders();rowsBuilt=now;
        return rowsCache=buildAvailableRows();
    }
    private List<Object> buildAvailableRows(){ArrayList<Object> rows=new ArrayList<>();String q=search==null?"":search.getText().trim();if(!q.isEmpty())rows.addAll(index.search(q));else{for(String f:index.folders())if(parentOf(f).equals(folder))rows.add(new FolderRow(f));for(PackRecord p:index.snapshot())if(p.folderPath.equals(folder))rows.add(p);}synchronized(selected){rows.removeAll(selected);}rows.sort((a,b)->{if(a instanceof FolderRow&&!(b instanceof FolderRow))return-1;if(b instanceof FolderRow&&!(a instanceof FolderRow))return 1;return label(a).compareToIgnoreCase(label(b));});return rows;}
    @Override protected void actionPerformed(GuiButton b){try{
        if(b.id==0&&!applying)applyAndClose();else if(b.id==1){folderCounts.clear();index.refresh(true);}else if(b.id==2){leaving=true;mc.displayGuiScreen(parent);}else if(b.id==3&&!folder.isEmpty())setFolder(parentOf(folder));else if(b.id==4)mc.displayGuiScreen(new GuiFolderName(this,index,folder));else if(b.id==5&&availableChoice>=0){List<Object>a=availableRows();if(availableChoice<a.size()&&a.get(availableChoice)instanceof PackRecord)mc.displayGuiScreen(new GuiMovePack(this,index,(PackRecord)a.get(availableChoice)));}else if(b.id==6){config.transparentBackground=!config.transparentBackground;updateButton(6,backgroundLabel());saveConfig();}else if(b.id==7){config.previews=!config.previews;updateButton(7,previewLabel());clearHover();saveConfig();}else if(b.id==8){cycleClearGlass();updateButton(8,clearGlassLabel());}
    }catch(Throwable t){fail("button",t);}}
    @Override protected void mouseClicked(int x,int y,int button){try{super.mouseClicked(x,y,button);search.mouseClicked(x,y,button);if(button==1){nextPreviewGroup();return;}if(button!=0)return;List<Object> rows=availableRows();
        if(layout.availableList.contains(x,y)){int choice=availableScroll+(y-layout.availableList.top)/FastPacksLayout.ROW_HEIGHT;if(choice<rows.size()){availableChoice=choice;selectedChoice=-1;Object row=rows.get(choice);if(x>=layout.availableList.right-20)activateAvailable(row);else doubleActivate(choice,row);}}
        else if(layout.selectedList.contains(x,y)){int choice=selectedScroll+(y-layout.selectedList.top)/FastPacksLayout.ROW_HEIGHT;synchronized(selected){if(choice<selected.size()){selectedChoice=choice;availableChoice=-1;if(x>=layout.selectedList.right-45){if(x<layout.selectedList.right-30)moveSelected(-1);else if(x<layout.selectedList.right-15)moveSelected(1);else removeSelected(choice);}else doubleRemove(choice);}}}
        else if(layout.breadcrumb.contains(x,y))clickCrumb(x);
    }catch(Throwable t){fail("mouse input",t);}}
    private void doubleActivate(int choice,Object row){long now=System.currentTimeMillis();if(lastChoice==choice&&now-lastClick<350)activateAvailable(row);lastChoice=choice;lastClick=now;}
    private void doubleRemove(int choice){long now=System.currentTimeMillis();if(lastChoice==~choice&&now-lastClick<350)removeSelected(choice);lastChoice=~choice;lastClick=now;}
    private void activateAvailable(Object row){if(row instanceof FolderRow){setFolder(((FolderRow)row).path);search.setText("");}else synchronized(selected){selected.add((PackRecord)row);selVersion++;}}
    private void setFolder(String f){folder=f;availableScroll=0;availableChoice=-1;config.lastFolder=f;saveConfig();}
    private void removeSelected(int i){synchronized(selected){if(i>=0&&i<selected.size()){selected.remove(i);selVersion++;}selectedChoice=-1;}}
    private void moveSelected(int d){synchronized(selected){int n=selectedChoice+d;if(selectedChoice>=0&&n>=0&&n<selected.size()){Collections.swap(selected,selectedChoice,n);selectedChoice=n;}}}
    @Override protected void keyTyped(char c,int key){try{if(key==Keyboard.KEY_ESCAPE){leaving=true;mc.displayGuiScreen(parent);return;}if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){if(availableChoice>=0){List<Object>a=availableRows();if(availableChoice<a.size())activateAvailable(a.get(availableChoice));}else if(selectedChoice>=0)removeSelected(selectedChoice);return;}search.textboxKeyTyped(c,key);availableScroll=0;availableChoice=-1;}catch(Throwable t){fail("keyboard input",t);}}
    @Override public void handleMouseInput(){try{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0){int d=wheel>0?-3:3;int x=Mouse.getEventX()*width/Math.max(1,mc.displayWidth);if(x<width/2)availableScroll=Math.max(0,availableScroll+d);else selectedScroll=Math.max(0,selectedScroll+d);}}catch(Throwable t){fail("mouse wheel",t);}}
    @Override public void drawScreen(int mx,int my,float partial){try{
        drawFastBackground();drawCenteredString(fontRendererObj,"Fast Resource Packs",width/2,layout.title.top,0xffffff);search.drawTextBox();drawBreadcrumb();drawClipped("Available",layout.availableHeading,0xffffff);drawClipped("Selected (top has highest priority)",layout.selectedHeading,0xffffff);
        int panel=config.transparentBackground?0x66000000:0x33000000;drawRect(layout.availableList.left,layout.availableList.top,layout.availableList.right,layout.availableList.bottom,panel);drawRect(layout.selectedList.left,layout.selectedList.top,layout.selectedList.right,layout.selectedList.bottom,panel);
        List<Object> rows=availableRows();clampScroll(rows.size());PackRecord hovered=null;int hx=0,hy=0;enableClip(layout.availableList);int visible=layout.availableList.height()/FastPacksLayout.ROW_HEIGHT+2;
        for(int i=availableScroll;i<Math.min(rows.size(),availableScroll+visible);i++){int y=layout.availableList.top+(i-availableScroll)*FastPacksLayout.ROW_HEIGHT;drawAvailable(rows.get(i),i,layout.availableList.left,y,layout.availableList.right);if(rows.get(i)instanceof PackRecord&&insideRow(mx,my,layout.availableList,y)){hovered=(PackRecord)rows.get(i);hx=layout.availableList.left;hy=y;}}disableClip();
        enableClip(layout.selectedList);synchronized(selected){for(int i=selectedScroll;i<Math.min(selected.size(),selectedScroll+visible);i++){int y=layout.selectedList.top+(i-selectedScroll)*FastPacksLayout.ROW_HEIGHT;PackRecord p=selected.get(i);drawPack(p,i,layout.selectedList.left,y,layout.selectedList.right,i==selectedChoice,true);if(insideRow(mx,my,layout.selectedList,y)){hovered=p;hx=layout.selectedList.left;hy=y;}}}disableClip();
        if(index.isLoading())drawCenteredString(fontRendererObj,"Loading...",width/4,(layout.availableList.top+layout.availableList.bottom)/2,0xffff55);if(applying)drawCenteredString(fontRendererObj,"Applying...",width/2,layout.move.top+6,0xffff55);super.drawScreen(mx,my,partial);updateAndDrawPreview(hovered,hx,hy);
    }catch(Throwable t){fail("drawing",t);}}
    private boolean insideRow(int mx,int my,FastPacksLayout.Rect list,int y){return mx>=list.left&&mx<list.right&&my>=y&&my<y+FastPacksLayout.ROW_HEIGHT&&my<list.bottom;}
    private void drawFastBackground(){if(!config.transparentBackground){drawBackground(0);return;}if(mc.theWorld==null)drawRect(0,0,width,height,0xff181818);drawGradientRect(0,0,width,height,0x99101010,0x77101010);}
    private void drawAvailable(Object row,int i,int x,int y,int right){if(row instanceof FolderRow){drawRect(x+1,y+1,right-1,y+39,i==availableChoice?0x88557799:0x33222222);drawFolderIcon(x+4,y+7);String path=((FolderRow)row).path;drawClippedAt(LegacyText.clean(nameOf(path)),x+40,y+6,right-x-44,0xffff55);CompletableFuture<Integer> count=folderCounts.get(path);if(count==null){count=index.submit(()->{int n=0;String prefix=path+"/";for(PackRecord p:index.snapshot())if(p.folderPath.equals(path)||p.folderPath.startsWith(prefix))n++;return n;});folderCounts.put(path,count);}Integer n=count.getNow(null);drawClippedAt("Folder, "+(n==null?"...":n+" packs"),x+40,y+21,right-x-44,0xaaaaaa);}else drawPack((PackRecord)row,i,x,y,right,i==availableChoice,false);}
    private void drawFolderIcon(int x,int y){drawRect(x,y+4,x+28,y+27,0xffc79b45);drawRect(x+3,y,x+15,y+7,0xffdfb65d);drawRect(x+2,y+7,x+26,y+24,0xffffcf6a);}
    private void drawPack(PackRecord p,int i,int x,int y,int right,boolean chosen,boolean active){drawRect(x+1,y+1,right-1,y+39,chosen?0x88557799:0x33222222);drawIcon(p,x+4,y+4);int controls=active?48:22,textRight=right-controls;String title=LegacyText.cleanName(p.name)+(p.exists()?"":" [missing]");drawClippedAt(title,x+40,y+6,textRight-x-42,p.exists()?0xffffff:0xff5555);PackMetadata m=index.metadata(p).getNow(null);String desc=m==null?"Loading metadata...":LegacyText.clean(m.description)+(m.format!=1&&m.format>=0?" [format "+m.format+"]":"");if(!search.getText().trim().isEmpty())desc=p.folderPath+(p.folderPath.isEmpty()?"":" - ")+desc;drawClippedAt(desc,x+40,y+21,textRight-x-42,0xaaaaaa);if(active){drawString(fontRendererObj,"^",right-41,y+7,0xffffff);drawString(fontRendererObj,"v",right-26,y+7,0xffffff);drawString(fontRendererObj,"x",right-11,y+7,0xff7777);}else drawString(fontRendererObj,"+",right-14,y+14,0x77ff77);drawInlinePreview(p,x+40,y,textRight,chosen?0x88557799:0x33222222);}
    private void drawIcon(PackRecord p,int x,int y){ResourceLocation loc=icons.get(p.relativePath);BufferedImage image=index.icon(p).getNow(null);if(loc==null&&image!=null){loc=mc.getTextureManager().getDynamicTextureLocation("fastpacks/icon/"+Integer.toHexString(p.relativePath.hashCode()),new DynamicTexture(image));icons.put(p.relativePath,loc);}if(loc==null){drawRect(x,y,x+32,y+32,0xff555555);drawCenteredString(fontRendererObj,"?",x+16,y+12,0xffffff);return;}mc.getTextureManager().bindTexture(loc);GlStateManager.color(1,1,1,1);drawModalRectWithCustomSizedTexture(x,y,0,0,32,32,32,32);}
    private static final int PREVIEW_DELAY = 0, GROUP_MS = 1100, FADE_MS = 220, PREVIEW_ICON = 18;

    /** Tracks which pack the mouse rests on and starts reading its textures; the slideshow itself is drawn inside the row. */
    private void updateAndDrawPreview(PackRecord p, int rowX, int rowY) {
        if (!config.previews || p == null) { clearHover(); return; }
        String key = p.relativePath;
        if (!key.equals(hoveredKey)) { hoveredKey = key; hoverSince = System.currentTimeMillis(); previewBase = 0; previewClock = hoverSince - FADE_MS; }
        if (System.currentTimeMillis() - hoverSince < PREVIEW_DELAY) return;
        if (previewCache.get(key) == null) {
            previewCache.put(key, new PreviewEntry(index.submit(() -> PackPreviewReader.read(p.file))));
            evictPreviews();
        }
    }

    /** Replaces the description line of the hovered pack's row with a row of icons that cycles swords, armor, tools, blocks and so on. */
    private void drawInlinePreview(PackRecord p, int x, int y, int textRight, int rowColour) {
        if (!config.previews || !p.relativePath.equals(hoveredKey)) return;
        long now = System.currentTimeMillis();
        if (now - hoverSince < PREVIEW_DELAY) return;
        PreviewEntry e = previewCache.get(p.relativePath);
        if (e == null) return;
        int lineY = y + 19;
        drawRect(x - 2, lineY - 1, textRight, lineY + PREVIEW_ICON + 1, 0xff1c1c1c);
        PackPreview data = e.future.getNow(null);
        if (data == null) { drawString(fontRendererObj, "Loading preview...", x, lineY + 5, 0x999999); return; }
        if (data.items.isEmpty()) { drawString(fontRendererObj, "No item or block textures in this pack", x, lineY + 5, 0x999999); return; }
        upload(e, data, p.relativePath);
        long elapsed = Math.max(0, now - previewClock);
        int slot = previewBase + (int) (elapsed / GROUP_MS);
        int group = data.groups.get(slot % data.groups.size());
        long t = elapsed % GROUP_MS;
        float alpha = Math.min(1f, Math.min(t, GROUP_MS - t) / (float) FADE_MS);
        if (data.groups.size() == 1) alpha = 1f;
        int shown = 0;
        for (int i = 0; i < data.items.size(); i++) {
            if (data.items.get(i).group != group) continue;
            int dx = x + shown * (PREVIEW_ICON + 4);
            if (dx + PREVIEW_ICON > textRight) break;
            drawPreviewItem(e, data, i, dx, lineY, alpha);
            shown++;
        }
        int labelX = x + shown * (PREVIEW_ICON + 4) + 2;
        if (labelX + 30 < textRight) {
            int a = Math.max(4, (int) (alpha * 255)) << 24;
            drawString(fontRendererObj, PackPreviewReader.GROUP_LABELS[group], labelX, lineY + 5, 0x00888888 | a);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private void upload(PreviewEntry e, PackPreview data, String key) {
        if (!e.locations.isEmpty()) return;
        for (int i = 0; i < data.items.size(); i++) {
            DynamicTexture texture = new DynamicTexture(data.items.get(i).image);
            texture.setBlurMipmapDirect(false, false);
            e.locations.add(mc.getTextureManager().getDynamicTextureLocation("fastpacks/preview/" + Integer.toHexString((key + "/" + i).hashCode()), texture));
        }
    }

    private void drawPreviewItem(PreviewEntry e, PackPreview data, int i, int x, int y, float alpha) {
        if (alpha <= 0 || i >= e.locations.size()) return;
        drawRect(x, y, x + PREVIEW_ICON, y + PREVIEW_ICON, (int) (alpha * 40) << 24 | 0xffffff);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, alpha);
        mc.getTextureManager().bindTexture(e.locations.get(i));
        // the textures are 16..64 px squares drawn into an 18 px slot, so scale the whole texture rather than tile it
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + PREVIEW_ICON, 0).tex(0, 1).endVertex();
        wr.pos(x + PREVIEW_ICON, y + PREVIEW_ICON, 0).tex(1, 1).endVertex();
        wr.pos(x + PREVIEW_ICON, y, 0).tex(1, 0).endVertex();
        wr.pos(x, y, 0).tex(0, 0).endVertex();
        tessellator.draw();
        GlStateManager.color(1, 1, 1, 1);
    }
    private void evictPreviews(){while(previewCache.size()>16){Iterator<Map.Entry<String,PreviewEntry>>it=previewCache.entrySet().iterator();PreviewEntry e=it.next().getValue();it.remove();release(e);}}
    private int previewBase;
    private long previewClock;

    /** Right click: jump straight to the next group of the hovered pack's preview (swords, armor, tools...), shown at full strength at once. */
    private void nextPreviewGroup() {
        if (hoveredKey == null || !config.previews) return;
        previewBase++;
        previewClock = System.currentTimeMillis() - FADE_MS;
    }

    private void clearHover(){hoveredKey=null;hoverSince=0;}
    private void release(PreviewEntry e){for(ResourceLocation r:e.locations)try{mc.getTextureManager().deleteTexture(r);}catch(Throwable ignored){}e.locations.clear();}
    private void releaseTextures(){for(ResourceLocation r:icons.values())try{mc.getTextureManager().deleteTexture(r);}catch(Throwable ignored){}icons.clear();for(PreviewEntry e:previewCache.values())release(e);previewCache.clear();}
    private void applyAndClose(){applying=true;final List<PackRecord>wanted;synchronized(selected){wanted=new ArrayList<>(selected);}index.submit(()->{List<ResourcePackRepository.Entry>entries=new ArrayList<>();ResourcePackRepository repo=mc.getResourcePackRepository();Map<String,ResourcePackRepository.Entry> known=new HashMap<>();for(ResourcePackRepository.Entry k:new ArrayList<>(repo.getRepositoryEntriesAll()))known.put(k.getResourcePackName(),k);for(int i=wanted.size()-1;i>=0;i--){PackRecord p=wanted.get(i);if(!p.exists())continue;ResourcePackRepository.Entry e=known.get(p.relativePath);if(e==null){e=ResourcePackEntryInvoker.fastpacks$create(repo,p.file);e.updateResourcePack();}entries.add(e);}return entries;}).whenComplete((entries,error)->mc.addScheduledTask(()->{try{if(error!=null)throw new RuntimeException(error);ResourcePackRepository repo=mc.getResourcePackRepository();repo.setRepositories(entries);mc.gameSettings.resourcePacks.clear();mc.gameSettings.incompatibleResourcePacks.clear();for(int i=wanted.size()-1;i>=0;i--){PackRecord p=wanted.get(i);if(!p.exists())continue;mc.gameSettings.resourcePacks.add(p.relativePath);PackMetadata m=index.metadata(p).getNow(PackMetadata.MISSING);if(m.format!=1)mc.gameSettings.incompatibleResourcePacks.add(p.relativePath);}mc.gameSettings.saveOptions();mc.refreshResources();leaving=true;mc.displayGuiScreen(parent);}catch(Throwable t){applying=false;fail("apply",t);}}));}
    private void drawBreadcrumb(){drawClippedAt("/resourcepacks/"+(folder.isEmpty()?"":folder+"/"),layout.breadcrumb.left,layout.breadcrumb.top,layout.breadcrumb.width(),0xdddddd);}
    private void clickCrumb(int x){String root="/resourcepacks/";int pos=layout.breadcrumb.left+fontRendererObj.getStringWidth(root);if(x<pos){setFolder("");return;}String built="";for(String part:folder.split("/")){built=built.isEmpty()?part:built+"/"+part;pos+=fontRendererObj.getStringWidth(part+"/");if(x<pos){setFolder(built);return;}}}
    private void clampScroll(int count){int cap=Math.max(0,count-Math.max(1,layout.availableList.height()/FastPacksLayout.ROW_HEIGHT));availableScroll=Math.min(availableScroll,cap);synchronized(selected){selectedScroll=Math.min(selectedScroll,Math.max(0,selected.size()-Math.max(1,layout.selectedList.height()/FastPacksLayout.ROW_HEIGHT)));}}
    private void drawClipped(String s,FastPacksLayout.Rect r,int color){drawClippedAt(s,r.left,r.top,r.width(),color);}
    private void drawClippedAt(String value,int x,int y,int pixels,int color){String s=LegacyText.clean(value);if(pixels<=0)return;if(fontRendererObj.getStringWidth(s)>pixels){String dots="...";int dotWidth=fontRendererObj.getStringWidth(dots);s=dotWidth>pixels?fontRendererObj.trimStringToWidth(dots,pixels):fontRendererObj.trimStringToWidth(s,pixels-dotWidth)+dots;}drawString(fontRendererObj,s,x,y,color);}
    private static String parentOf(String p){int i=p.lastIndexOf('/');return i<0?"":p.substring(0,i);}private static String nameOf(String p){int i=p.lastIndexOf('/');return i<0?p:p.substring(i+1);}private static String label(Object o){return o instanceof FolderRow?((FolderRow)o).path:((PackRecord)o).relativePath;}
    private static final class FolderRow{final String path;FolderRow(String p){path=p;}}
    private static final class PreviewEntry{final CompletableFuture<PackPreview>future;final List<ResourceLocation>locations=new ArrayList<>();PreviewEntry(CompletableFuture<PackPreview>f){future=f;}}
    private static void enableClip(FastPacksLayout.Rect r){Minecraft m=Minecraft.getMinecraft();double sx=(double)m.displayWidth/Math.max(1,m.currentScreen.width),sy=(double)m.displayHeight/Math.max(1,m.currentScreen.height);GL11.glEnable(GL11.GL_SCISSOR_TEST);int x0=(int)Math.floor(r.left*sx),x1=(int)Math.ceil(r.right*sx),y0=(int)Math.floor(r.top*sy),y1=(int)Math.ceil(r.bottom*sy);GL11.glScissor(x0,m.displayHeight-y1,Math.max(0,x1-x0),Math.max(0,y1-y0));}private static void disableClip(){GL11.glDisable(GL11.GL_SCISSOR_TEST);}
    private void saveConfig(){config.lastFolder=folder;config.save(mc.mcDataDir);}
    private void fail(String where,Throwable t){try{leaving=true;FastPacks.openVanilla(mc,parent,where,t);}catch(Throwable ignored){FastPacks.reportOnce(where,t);}}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);releaseTextures();saveConfig();if(leaving&&index!=null)index.close();}
}
