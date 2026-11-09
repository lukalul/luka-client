package dev.fastpacks.index;

import dev.fastpacks.gui.FastPacksLayout;
import dev.fastpacks.gui.LegacyText;
import dev.fastpacks.preview.PackPreview;
import dev.fastpacks.preview.PackPreviewReader;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;
import javax.imageio.ImageIO;

public final class PackIndexSelfTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("fastpacks-selftest-");
        try {
            long create = System.nanoTime();
            for (int i=0;i<2000;i++) {
                Path dir = root.resolve("folder"+(i%20)); Files.createDirectories(dir);
                try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(dir.resolve("pack-"+i+".zip")))) {
                    z.putNextEntry(new ZipEntry("pack.mcmeta")); z.write(("{\"pack\":{\"pack_format\":1,\"description\":\"Generated "+i+"\"}}").getBytes("UTF-8")); z.closeEntry();
                }
            }
            long start=System.nanoTime();
            try (PackIndex index = new PackIndex(root.toFile())) {
                List<PackRecord> result=index.refresh(true).get(30, java.util.concurrent.TimeUnit.SECONDS);
                long indexed=System.nanoTime(); List<PackRecord> found=index.search("pack-1999"); long searched=System.nanoTime();
                if(result.size()!=2000 || found.size()!=1) throw new AssertionError("index="+result.size()+", search="+found.size());
                System.out.printf("FastPacks self-check: generated=2000 create=%.1fms index=%.1fms search=%.3fms%n", (start-create)/1e6,(indexed-start)/1e6,(searched-indexed)/1e6);
            }
            checkPreviews(root.resolve("preview-fixtures"));
            checkText();
            checkLayouts();
            System.out.println("FastPacks Task 2 checks: preview zip+directory+missing+animated+512px PASS; legacy text+clip PASS; layouts scales 1-4 PASS");
        } finally { delete(root); }
    }

    private static void checkPreviews(Path fixtures) throws Exception {
        Files.createDirectories(fixtures);
        Path zip=fixtures.resolve("items.zip");
        try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(zip))){
            z.putNextEntry(new ZipEntry("assets/minecraft/textures/items/diamond_sword.png"));z.write(png(image(16,32,Color.RED,Color.BLUE)));z.closeEntry();
        }
        PackPreview zipped=PackPreviewReader.read(zip.toFile());
        if(zipped.items.size()!=1||zipped.items.get(0).image.getHeight()!=16||zipped.items.get(0).image.getRGB(2,2)!=Color.RED.getRGB())throw new AssertionError("animated zip first-frame preview failed");
        Path dir=fixtures.resolve("directory-pack/assets/minecraft/textures/items");Files.createDirectories(dir);ImageIO.write(image(512,512,Color.GREEN,Color.GREEN),"png",dir.resolve("diamond_helmet.png").toFile());
        PackPreview directory=PackPreviewReader.read(fixtures.resolve("directory-pack").toFile());
        if(directory.items.size()!=1||directory.items.get(0).image.getWidth()!=64)throw new AssertionError("large directory preview failed");
        Path empty=fixtures.resolve("empty.zip");try(ZipOutputStream ignored=new ZipOutputStream(Files.newOutputStream(empty))){}
        if(!PackPreviewReader.read(empty.toFile()).items.isEmpty())throw new AssertionError("missing-items preview failed");
    }
    private static BufferedImage image(int w,int h,Color first,Color rest){BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setRGB(x,y,y<w?first.getRGB():rest.getRGB());return image;}
    private static byte[] png(BufferedImage image)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
    private static void checkText(){
        String cleaned=LegacyText.cleanName("! Â§9Â§lDeproved   Â§bÂ§lEdit.zip");
        if(!cleaned.equals("§9§lDeproved §b§lEdit"))throw new AssertionError("legacy clean: "+cleaned);
        LegacyText.Width metric=s->s.replaceAll("§.","").length()*6;
        String clipped=LegacyText.clip("§a123456789",36,metric);
        if(metric.width(clipped)>36||!clipped.endsWith("..."))throw new AssertionError("clip: "+clipped);
    }
    private static void checkLayouts(){int[][] displays={{854,480},{1920,1080}};for(int[]d:displays)for(int requested=1;requested<=4;requested++){int scale=1;while(scale<requested&&d[0]/(scale+1)>=320&&d[1]/(scale+1)>=240)scale++;FastPacksLayout l=FastPacksLayout.calculate((int)Math.ceil(d[0]/(double)scale),(int)Math.ceil(d[1]/(double)scale));if(l.nav.overlaps(l.breadcrumb)||l.breadcrumb.overlaps(l.availableHeading)||l.availableHeading.overlaps(l.availableList)||l.availableList.overlaps(l.move)||l.move.overlaps(l.done)||l.selectedList.overlaps(l.previews)||l.previews.overlaps(l.cancel))throw new AssertionError("layout overlap "+d[0]+"x"+d[1]+" scale "+requested);}}
    private static void delete(Path p) throws IOException { if(!Files.exists(p)) return; try(java.util.stream.Stream<Path> s=Files.walk(p)){ s.sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(IOException ignored){}}); } }
}
