package com.nexus.ai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NexusAccessibilityService extends AccessibilityService {
    public interface ResultCallback { void onResult(String result); }
    private static NexusAccessibilityService instance;
    private WindowManager wm;
    private View highlight;
    private View highlightLabel;
    private ScreenAssistController screenAssist;

    public static NexusAccessibilityService get(){ return instance; }
    public static boolean isEnabled(){ return instance != null; }

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        instance=this;
        AccessibilityServiceInfo info=getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        screenAssist=new ScreenAssistController(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(screenAssist!=null) screenAssist.onScreenChanged();
    }
    @Override public void onInterrupt(){}

    @Override public void onDestroy(){
        if(screenAssist!=null){ try{ screenAssist.hide(); }catch(Exception ignored){} }
        if(highlight!=null && wm!=null){ try{ wm.removeView(highlight); }catch(Exception ignored){} }
        if(highlightLabel!=null && wm!=null){ try{ wm.removeView(highlightLabel); }catch(Exception ignored){} }
        if(instance==this) instance=null;
        super.onDestroy();
    }

    public void showAssistantOverlay(){
        if(screenAssist==null) screenAssist=new ScreenAssistController(this);
        screenAssist.show();
    }

    public void hideAssistantOverlay(){
        if(screenAssist!=null) screenAssist.hide();
    }

    public void askAssistantOverlay(String question){
        if(screenAssist==null) screenAssist=new ScreenAssistController(this);
        screenAssist.ask(question==null ? "" : question);
    }

    public void highlightText(String query){
        if(query==null || query.trim().isEmpty()) return;
        AccessibilityNodeInfo node=find(getRootInActiveWindow(),query.trim());
        if(node==null) return;
        Rect rect=new Rect();
        node.getBoundsInScreen(rect);
        showHighlight(rect);
    }

    public void executeCommand(String command, ResultCallback callback){
        if(command==null || command.trim().isEmpty()){ callback.onResult("Порожня команда."); return; }
        String[] steps=command.split("(?i)\\s*(?:;|\\bі потім\\b|\\bа потім\\b)\\s*");
        List<String> results=new ArrayList<>();
        for(String step:steps){
            if(!step.trim().isEmpty()) results.add(executeOne(step.trim()));
        }
        callback.onResult(String.join("\n",results));
    }

    private String executeOne(String original){
        String s=original.toLowerCase(Locale.ROOT).trim();

        if(any(s,"що на екрані","шо на екрані","прочитай екран","розкажи що тут")){
            String text=screenText();
            return text.isEmpty() ? "Не бачу доступного тексту на цьому екрані." : text;
        }

        if(s.startsWith("знайди на екрані ")){
            String q=original.substring("знайди на екрані ".length()).trim();
            AccessibilityNodeInfo node=find(getRootInActiveWindow(),q);
            if(node==null) return "Не знайшов на екрані: "+q;
            Rect rect=new Rect(); node.getBoundsInScreen(rect); showHighlight(rect);
            return "Знайшов: "+q;
        }

        if(s.startsWith("натисни ") || s.startsWith("нажми ")){
            String q=original.substring(original.indexOf(' ')+1).trim();
            AccessibilityNodeInfo node=find(getRootInActiveWindow(),q);
            if(node==null) return "Не знайшов елемент: "+q;
            AccessibilityNodeInfo clickable=clickable(node);
            boolean ok=clickable!=null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return ok ? "Натиснув: "+q : "Знайшов елемент, але Android не дозволив натиснути.";
        }

        if(any(s,"прокрути вниз","скрол вниз","нижче")){
            return scroll(true) ? "Прокрутив вниз." : "Не знайшов область для прокрутки.";
        }
        if(any(s,"прокрути вгору","скрол вгору","вище")){
            return scroll(false) ? "Прокрутив вгору." : "Не знайшов область для прокрутки.";
        }
        if(any(s,"повернись назад","назад")){
            return performGlobalAction(GLOBAL_ACTION_BACK) ? "Назад." : "Не вдалося повернутися назад.";
        }
        if(any(s,"головний екран","додому")){
            return performGlobalAction(GLOBAL_ACTION_HOME) ? "Відкрив головний екран." : "Не вдалося.";
        }
        if(any(s,"останні додатки","недавні додатки")){
            return performGlobalAction(GLOBAL_ACTION_RECENTS) ? "Відкрив недавні додатки." : "Не вдалося.";
        }

        String action=settingAction(s);
        if(action!=null){
            try{
                startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "Відкрив системний розділ.";
            }catch(Exception e){
                return "Android не дозволив відкрити цей системний розділ.";
            }
        }
        return "Не знайшов локальної дії для цієї команди.";
    }

    private String settingAction(String s){
        String n=normalize(s);
        if(fuzzyWord(n,"інтернет",2) || fuzzyWord(n,"мережа",2) || any(n,"network")) return Settings.ACTION_WIRELESS_SETTINGS;
        if(any(n,"память телефона","сховище телефона","storage") || fuzzyWord(n,"память",2)) return Settings.ACTION_INTERNAL_STORAGE_SETTINGS;
        if(any(n,"wifi","wi fi","вай фай") || fuzzyWord(n,"вайфай",2)) return Settings.ACTION_WIFI_SETTINGS;
        if(any(n,"bluetooth") || fuzzyWord(n,"блютуз",2)) return Settings.ACTION_BLUETOOTH_SETTINGS;
        if(fuzzyWord(n,"батарея",2) || any(n,"акумулятор")) return Settings.ACTION_BATTERY_SAVER_SETTINGS;
        if(any(n,"display settings") || fuzzyWord(n,"яскравість",3) || fuzzyWord(n,"екран",2)) return Settings.ACTION_DISPLAY_SETTINGS;
        if(fuzzyWord(n,"сповіщення",3) || any(n,"уведомлен")) return "android.settings.NOTIFICATION_SETTINGS";
        if(fuzzyWord(n,"додатки",2) || any(n,"програми")) return Settings.ACTION_APPLICATION_SETTINGS;
        if(fuzzyWord(n,"спецможливості",4) || any(n,"accessibility")) return Settings.ACTION_ACCESSIBILITY_SETTINGS;
        if(fuzzyWord(n,"безпека",2) || any(n,"security")) return Settings.ACTION_SECURITY_SETTINGS;
        if(fuzzyWord(n,"налаштування",4)) return Settings.ACTION_SETTINGS;
        return null;
    }

    private String normalize(String value){
        if(value==null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replace('’','\'')
                .replace('ʼ','\'')
                .replaceAll("[^а-яіїєґa-z0-9' ]+"," ")
                .replaceAll("\\s+"," ")
                .trim();
    }

    private boolean fuzzyWord(String text,String target,int maxDistance){
        String t=normalize(target);
        String[] words=normalize(text).split(" ");
        for(String word:words){
            if(word.isEmpty()) continue;
            if(word.equals(t) || word.startsWith(t)) return true;
            if(distance(word,t)<=maxDistance) return true;
        }
        return false;
    }

    private int distance(String a,String b){
        int[] prev=new int[b.length()+1];
        int[] cur=new int[b.length()+1];
        for(int j=0;j<=b.length();j++) prev[j]=j;

        for(int i=0;i<a.length();i++){
            cur[0]=i+1;
            for(int j=0;j<b.length();j++){
                int cost=a.charAt(i)==b.charAt(j)?0:1;
                cur[j+1]=Math.min(
                        Math.min(cur[j]+1,prev[j+1]+1),
                        prev[j]+cost
                );
            }
            int[] swap=prev; prev=cur; cur=swap;
        }
        return prev[b.length()];
    }

    public String screenText(){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) return "";
        Set<String> out=new LinkedHashSet<>();
        collect(root,out,0);
        List<String> list=new ArrayList<>(out);
        if(list.size()>45) list=list.subList(0,45);
        return String.join(" · ",list);
    }

    private void collect(AccessibilityNodeInfo node, Set<String> out, int depth){
        if(node==null || depth>24 || out.size()>80) return;
        add(out,node.getText());
        add(out,node.getContentDescription());
        for(int i=0;i<node.getChildCount();i++) collect(node.getChild(i),out,depth+1);
    }

    private void add(Set<String> out, CharSequence value){
        if(value==null) return;
        String s=value.toString().trim().replaceAll("\\s+"," ");
        if(!s.isEmpty() && s.length()<=220) out.add(s);
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String query){
        if(node==null || TextUtils.isEmpty(query)) return null;
        String q=query.toLowerCase(Locale.ROOT);
        CharSequence text=node.getText();
        CharSequence desc=node.getContentDescription();
        if((text!=null && text.toString().toLowerCase(Locale.ROOT).contains(q))
                || (desc!=null && desc.toString().toLowerCase(Locale.ROOT).contains(q))) return node;
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo child=find(node.getChild(i),query);
            if(child!=null) return child;
        }
        return null;
    }

    private AccessibilityNodeInfo clickable(AccessibilityNodeInfo node){
        AccessibilityNodeInfo cur=node;
        for(int i=0;i<7 && cur!=null;i++){
            if(cur.isClickable()) return cur;
            cur=cur.getParent();
        }
        return null;
    }

    private AccessibilityNodeInfo scrollable(AccessibilityNodeInfo node){
        if(node==null) return null;
        if(node.isScrollable()) return node;
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo child=scrollable(node.getChild(i));
            if(child!=null) return child;
        }
        return null;
    }

    private boolean scroll(boolean down){
        AccessibilityNodeInfo n=scrollable(getRootInActiveWindow());
        return n!=null && n.performAction(down ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    private boolean any(String s,String... keys){
        for(String key:keys) if(s.contains(key)) return true;
        return false;
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    private void showHighlight(Rect rect){
        if(wm==null || rect==null || rect.isEmpty()) return;

        if(highlight!=null){ try{ wm.removeView(highlight); }catch(Exception ignored){} }
        if(highlightLabel!=null){ try{ wm.removeView(highlightLabel); }catch(Exception ignored){} }

        View view=new View(this);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.argb(28,255,255,255));
        bg.setStroke(dp(3),Color.WHITE);
        bg.setCornerRadius(dp(10));
        view.setBackground(bg);

        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
                Math.max(dp(12),rect.width()+dp(8)),Math.max(dp(12),rect.height()+dp(8)),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.START;
        p.x=Math.max(0,rect.left-dp(4));
        p.y=Math.max(0,rect.top-dp(4));

        android.widget.TextView label=new android.widget.TextView(this);
        label.setText("Натисни сюди");
        label.setTextColor(Color.BLACK);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(10),dp(5),dp(10),dp(5));
        GradientDrawable lbg=new GradientDrawable();
        lbg.setColor(Color.WHITE);
        lbg.setCornerRadius(dp(14));
        label.setBackground(lbg);

        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,dp(32),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START;
        lp.x=Math.max(dp(4),rect.left);
        lp.y=Math.max(dp(4),rect.top-dp(38));

        highlight=view;
        highlightLabel=label;
        try{
            wm.addView(view,p);
            wm.addView(label,lp);

            view.animate().alpha(0.45f).setDuration(260).withEndAction(
                    ()->view.animate().alpha(1f).setDuration(260).start()
            ).start();

            view.postDelayed(()->{
                if(highlight==view){
                    try{ wm.removeView(view); }catch(Exception ignored){}
                    highlight=null;
                }
                if(highlightLabel==label){
                    try{ wm.removeView(label); }catch(Exception ignored){}
                    highlightLabel=null;
                }
            },2200);
        }catch(Exception e){
            highlight=null;
            highlightLabel=null;
        }
    }
}
