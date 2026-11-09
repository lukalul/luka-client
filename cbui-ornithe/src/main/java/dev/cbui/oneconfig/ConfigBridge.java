package dev.cbui.oneconfig;

import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.Node;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;

/** The only class linked to OneConfig: converts its live trees to CBUI's safe, Minecraft-free row model. */
public final class ConfigBridge {
    private static final Set<String> REPORTED = new HashSet<>();

    public static ConfigSnapshot snapshot() {
        try { return fromTrees(ConfigManager.active().trees(), true); }
        catch (Throwable error) { reportOnce("snapshot", error); return ConfigSnapshot.empty(); }
    }

    /** Public for the plain-Java synthetic-tree check. */
    public static ConfigSnapshot fromTrees(Collection<Tree> trees, boolean liveSaving) {
        List<ConfigSnapshot.Mod> mods=new ArrayList<>();
        if(trees!=null) for(Tree tree:trees) try { ConfigSnapshot.Mod mod=mapTree(tree, liveSaving); if(!mod.sections.isEmpty()&&!mod.name.endsWith(".json")) mods.add(mod); } catch(Throwable error){reportOnce("tree "+safeId(tree),error);}
        Collections.sort(mods, Comparator.comparing((ConfigSnapshot.Mod m)->m.category).thenComparing(m->m.name,String.CASE_INSENSITIVE_ORDER));
        return new ConfigSnapshot(mods);
    }

    private static ConfigSnapshot.Mod mapTree(final Tree owner, boolean liveSaving) {
        final ConfigManager manager=liveSaving?ConfigManager.active():null;
        final Object categoryObject=owner.getMetadata("category");
        String category=categoryObject==null?"OTHER":callString(categoryObject,"getName",categoryObject.toString());
        category=category.substring(category.lastIndexOf('.')+1).trim();
        category=category.isEmpty()?"OTHER":category.substring(0,1).toUpperCase()+category.substring(1).toLowerCase();
        String title=text(owner.getTitle(),owner.getID());
        Tree defaults=null;
        if(manager!=null)try { defaults=ConfigManager.backup().get(owner.getID()); } catch(Throwable ignored) { }
        Map<String,List<ConfigSnapshot.Row>> sections=new LinkedHashMap<>();
        collect(owner,owner,defaults,"General",sections,manager);
        List<ConfigSnapshot.Section> result=new ArrayList<>();
        for(Map.Entry<String,List<ConfigSnapshot.Row>> e:sections.entrySet())if(!e.getValue().isEmpty())result.add(new ConfigSnapshot.Section(readable(e.getKey(),title),e.getValue()));
        return new ConfigSnapshot.Mod(owner.getID(),title,category,result);
    }

    /** Config file names ("scroll-tweaks.json") are not headings people can read: use the mod's title for them. */
    private static String readable(String section, String title) {
        return section != null && section.toLowerCase().endsWith(".json") ? title : section;
    }

    private static void collect(final Tree owner, Tree tree, Tree defaults, String inheritedSection,
                                Map<String,List<ConfigSnapshot.Row>> sections, final ConfigManager manager) {
        for(Node node:tree.map.values()) {
            try {
                if(Boolean.TRUE.equals(node.getMetadata("hidden")))continue;
                if(node instanceof Tree){Tree child=(Tree)node;Tree childDefaults=defaults==null?null:defaults.getChild(child.getID());collect(owner,child,childDefaults,text(child.getTitle(),child.getID()),sections,manager);continue;}
                if(!(node instanceof Property))continue;
                final Property<?> property=(Property<?>)node;
                String section=text(property.getMetadata("subcategory"),text(property.getMetadata("category"),inheritedSection));
                List<ConfigSnapshot.Row> rows=sections.get(section);if(rows==null){rows=new ArrayList<>();sections.put(section,rows);}
                Property<?> defaultProperty=defaults==null?null:defaults.getProp(property.getID());
                Object defaultValue=defaultProperty==null?null:safeGet(defaultProperty);
                rows.add(mapProperty(owner,property,defaultValue,manager));
            } catch(Throwable error){reportOnce("property "+safeId(node),error);}
        }
    }

    private static ConfigSnapshot.Row mapProperty(final Tree owner, final Property<?> property, Object defaultValue, final ConfigManager manager) {
        Object visualizer=property.getMetadata("visualizer");
        String visualizerName=visualizer==null?"":(visualizer instanceof Class?((Class<?>)visualizer).getSimpleName():visualizer.getClass().getSimpleName());
        ConfigSnapshot.Kind kind=kind(visualizerName,property.type);
        double[] bounds=bounds(property.getMetadata("bounds"));
        if(Double.isNaN(bounds[0])) { bounds[0]=number(property.getMetadata("min"),0); bounds[1]=number(property.getMetadata("max"),100); bounds[2]=number(property.getMetadata("step"),kind==ConfigSnapshot.Kind.SLIDER?1:0); }
        List<String> options=strings(property.getMetadata("options"));
        if(options.isEmpty()) options=strings(readMember(visualizer,"getOptions","options"));
        if(kind==ConfigSnapshot.Kind.BUTTON){Object caption=property.getMetadata("text");if(caption!=null)options=Collections.singletonList(text(caption,"RUN"));}
        final Runnable runnable=property.getMetadata("runnable");
        final ConfigSnapshot.Kind rowKind=kind;
        ConfigSnapshot.Accessor accessor=new ConfigSnapshot.Accessor(){
            public Object get(){return rowKind==ConfigSnapshot.Kind.BUTTON?runnable:property.get();}
            @SuppressWarnings({"rawtypes","unchecked"}) public boolean set(Object value){if(rowKind==ConfigSnapshot.Kind.BUTTON){if(runnable!=null)runnable.run();return true;}Object before=property.get();boolean cancelled=((Property)property).set(value);return !cancelled&&!Objects.equals(before,property.get());}
            public ConfigSnapshot.Display display(){Property.Display d=property.getDisplay();return d==Property.Display.HIDDEN?ConfigSnapshot.Display.HIDDEN:d==Property.Display.DISABLED?ConfigSnapshot.Display.DISABLED:ConfigSnapshot.Display.SHOWN;}
            public void save(){if(manager!=null)manager.save(owner);}
            public void openFallback() throws Exception {openOneConfig(owner);}
        };
        boolean noAlpha=Boolean.TRUE.equals(property.getMetadata("noAlpha"));
        Object alpha=property.getMetadata("alpha");if(alpha instanceof Boolean)noAlpha=!((Boolean)alpha);
        return new ConfigSnapshot.Row(kind,property.getID(),text(property.getTitle(),property.getID()),text(property.description,""),bounds[0],bounds[1],bounds[2],options,noAlpha,defaultValue,accessor);
    }

    static ConfigSnapshot.Kind kind(String name, Class<?> type) {
        String n=name==null?"":name.toLowerCase();
        if(n.contains("switch")||n.contains("checkbox"))return ConfigSnapshot.Kind.TOGGLE;
        if(n.contains("rangeslider")||n.contains("rangenumber")||n.contains("list")||n.contains("file")||n.contains("effect")||n.contains("multiselect")||n.contains("chain"))return ConfigSnapshot.Kind.UNSUPPORTED;
        if(n.contains("slider"))return ConfigSnapshot.Kind.SLIDER;
        if(n.contains("number"))return ConfigSnapshot.Kind.NUMBER;
        if(n.contains("text"))return ConfigSnapshot.Kind.TEXT;
        if(n.contains("dropdown")||n.contains("radio"))return ConfigSnapshot.Kind.DROPDOWN;
        if(n.contains("color"))return ConfigSnapshot.Kind.COLOR;
        if(n.contains("keybind"))return ConfigSnapshot.Kind.KEYBIND;
        if(n.contains("button"))return ConfigSnapshot.Kind.BUTTON;
        if(n.contains("info"))return ConfigSnapshot.Kind.INFO;
        if(type==Boolean.TYPE||type==Boolean.class)return ConfigSnapshot.Kind.TOGGLE;
        if(Number.class.isAssignableFrom(box(type)))return ConfigSnapshot.Kind.NUMBER;
        if(type==String.class)return ConfigSnapshot.Kind.TEXT;
        return ConfigSnapshot.Kind.UNSUPPORTED;
    }

    private static double[] bounds(Object value){
        double[] none={Double.NaN,Double.NaN,Double.NaN};if(value==null)return none;
        if(value instanceof double[]){double[] a=(double[])value;return new double[]{a.length>0?a[0]:0,a.length>1?a[1]:100,a.length>2?a[2]:1};}
        if(value instanceof float[]){float[] a=(float[])value;return new double[]{a.length>0?a[0]:0,a.length>1?a[1]:100,a.length>2?a[2]:1};}
        if(value instanceof List){List<?> a=(List<?>)value;return new double[]{number(a.size()>0?a.get(0):null,0),number(a.size()>1?a.get(1):null,100),number(a.size()>2?a.get(2):null,1)};}
        return none;
    }
    private static List<String> strings(Object value){if(value==null)return Collections.emptyList();List<String> out=new ArrayList<>();if(value instanceof Iterable)for(Object v:(Iterable<?>)value)out.add(text(v,""));else if(value.getClass().isArray())for(Object v:(Object[])value)out.add(text(v,""));return out;}
    private static Object readMember(Object target,String... names){if(target==null)return null;for(String name:names)try{Method m=target.getClass().getMethod(name);return m.invoke(target);}catch(Throwable ignored){}return null;}
    private static void openOneConfig(Tree tree)throws Exception {
        Class<?> ui=Class.forName("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI");Object config=findOwningConfig(tree);
        for(Method m:ui.getMethods())if(m.getName().equals("open")&&m.getParameterTypes().length==1){Class<?> p=m.getParameterTypes()[0];Object arg=p.isInstance(tree)?tree:p==String.class?tree.getID():config!=null&&p.isInstance(config)?config:null;if(arg!=null){m.invoke(null,arg);return;}}
        throw new NoSuchMethodException("OneConfigUI.open(config)");
    }
    private static Object findOwningConfig(Tree tree){try{java.lang.reflect.Field f=ConfigManager.class.getDeclaredField("initializedConfigs");f.setAccessible(true);Object values=f.get(null);if(values instanceof Map)for(Object config:((Map<?,?>)values).values()){Object owned=readMember(config,"getTree");if(owned==tree||owned instanceof Tree&&tree.getID().equals(((Tree)owned).getID()))return config;}}catch(Throwable ignored){}return null;}
    private static Object safeGet(Property<?> p){try{return p.get();}catch(Throwable ignored){return null;}}
    private static String safeId(Node node){try{return node==null?"?":node.getID();}catch(Throwable ignored){return "?";}}
    private static String text(Object value,String fallback){if(value==null)return fallback;Object translated=readMember(value,"get","resolve","getString");return translated==null?String.valueOf(value):String.valueOf(translated);}
    private static String callString(Object target,String method,String fallback){Object value=readMember(target,method);return value==null?fallback:String.valueOf(value);}
    private static double number(Object value,double fallback){return value instanceof Number?((Number)value).doubleValue():fallback;}
    private static Class<?> box(Class<?> c){if(c==null||!c.isPrimitive())return c==null?Object.class:c;if(c==Integer.TYPE)return Integer.class;if(c==Long.TYPE)return Long.class;if(c==Float.TYPE)return Float.class;if(c==Double.TYPE)return Double.class;if(c==Short.TYPE)return Short.class;if(c==Byte.TYPE)return Byte.class;return c;}
    public static synchronized void reportOnce(String area,Throwable error){if(!REPORTED.add(area))return;System.err.println("[CBUI/OneConfig] Skipping failing "+area);error.printStackTrace();}
    private ConfigBridge(){ }
}
