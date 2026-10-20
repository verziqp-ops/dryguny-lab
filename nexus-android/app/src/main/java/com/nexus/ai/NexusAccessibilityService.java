package com.nexus.ai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NexusAccessibilityService extends AccessibilityService {
    public interface ResultCallback { void onResult(String result); }
    private static NexusAccessibilityService instance;
    private WindowManager wm;
    private View handle,panel,highlight;
    private TextView panelResult;

    public static NexusAccessibilityService get(){return instance;}

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        instance=this;
        AccessibilityServiceInfo i=getServiceInfo();
        i.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(i);
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        showHandle();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){removeOverlays();if(instance==this)instance=null;super.onDestroy();}

    public void executeCommand(String command,ResultCallback cb){
        if(command==null||command.trim().isEmpty()){cb.onResult("Порожня команда.");return;}
        String[] steps=command.split("(?i)\\s*(?:;|\\bі потім\\b|\\bа потім\\b)\\s*");
        List<String> out=new ArrayList<>();
        for(String s:steps)if(!s.trim().isEmpty())out.add(one(s.trim()));
        cb.onResult(String.join("\n",out));
    }

    private String one(String original){
        String s=original.toLowerCase(Locale.ROOT).trim();
        if(any(s,"що на екрані","шо на екрані","розкажи що тут","прочитай екран")){
            String x=screenText();return x.isEmpty()?"Не бачу доступного тексту на цьому екрані.":"На екрані:\n"+x;
        }
        if(any(s,"переклади екран","переклад екрана","live translate")){
            String x=screenText();return x.isEmpty()?"Немає доступного тексту.":"Офлайн UI-переклад:\n"+translateUi(x);
        }
        if(s.startsWith("знайди на екрані ")){
            String q=original.substring("знайди на екрані ".length()).trim();
            AccessibilityNodeInfo n=find(getRootInActiveWindow(),q);
            if(n==null)return "Не знайшов на екрані: "+q;
            Rect r=new Rect();n.getBoundsInScreen(r);showHighlight(r);return "Знайшов «"+q+"» і підсвітив.";
        }
        if(s.startsWith("натисни ")||s.startsWith("нажми ")){
            String q=original.substring(original.indexOf(' ')+1).trim();
            AccessibilityNodeInfo n=find(getRootInActiveWindow(),q);
            if(n==null)return "Не знайшов елемент: "+q;
            AccessibilityNodeInfo c=clickable(n);boolean ok=c!=null&&c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return ok?"Натиснув: "+q:"Знайшов, але Android не дозволив натиснути.";
        }
        if(any(s,"прокрути вниз","скрол вниз","нижче"))return scroll(true)?"Прокрутив вниз.":"Немає елемента для прокрутки.";
        if(any(s,"прокрути вгору","скрол вгору","вище"))return scroll(false)?"Прокрутив вгору.":"Немає елемента для прокрутки.";
        if(any(s,"назад","повернись назад"))return performGlobalAction(GLOBAL_ACTION_BACK)?"Назад.":"Не вдалося.";
        if(any(s,"додому","головний екран"))return performGlobalAction(GLOBAL_ACTION_HOME)?"На головному екрані.":"Не вдалося.";
        if(any(s,"останні додатки","недавні додатки"))return performGlobalAction(GLOBAL_ACTION_RECENTS)?"Відкрив недавні.":"Не вдалося.";

        if(s.startsWith("запам'ятай ")||s.startsWith("запам’ятай ")||s.startsWith("запамятай ")){
            String v=original.substring(original.indexOf(' ')+1).trim();getSharedPreferences("memory",MODE_PRIVATE).edit().putString("last",v).apply();return "Зберіг локально: "+v;
        }
        if(s.contains("що ти пам")){String v=getSharedPreferences("memory",MODE_PRIVATE).getString("last","");return v.isEmpty()?"Локальна пам’ять порожня.":"Пам’ятаю: "+v;}

        String a=setting(s);
        if(a!=null){try{startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return "Відкриваю потрібний системний розділ.";}catch(Exception e){return "Android не дав відкрити цей розділ.";}}

        if(s.contains("що ти вмієш")||s.equals("допомога"))return "Можу офлайн: читати доступний текст екрана; натискати за назвою; шукати й підсвічувати елементи; скролити; назад/додому; відкривати системні налаштування; запам’ятовувати локальну нотатку; виконувати прості MultiStep-команди.";
        return "Для цього запиту потрібна повна локальна LLM. У v0.1 Screen Control та системні дії вже працюють.";
    }

    private String setting(String s){
        if(any(s,"пам'ять телефона","пам’ять телефона","память телефона","сховище телефона","storage"))return Settings.ACTION_INTERNAL_STORAGE_SETTINGS;
        if(any(s,"wifi","wi-fi","вайфай","вай фай"))return Settings.ACTION_WIFI_SETTINGS;
        if(any(s,"bluetooth","блютуз"))return Settings.ACTION_BLUETOOTH_SETTINGS;
        if(any(s,"батаре","акумулятор"))return Settings.ACTION_BATTERY_SAVER_SETTINGS;
        if(any(s,"налаштування екрана","яскравіст","display settings"))return Settings.ACTION_DISPLAY_SETTINGS;
        if(any(s,"сповіщення","уведомлен"))return "android.settings.NOTIFICATION_SETTINGS";
        if(any(s,"всі додатки","налаштування додатків","програми"))return Settings.ACTION_APPLICATION_SETTINGS;
        if(any(s,"спец можливост","accessibility"))return Settings.ACTION_ACCESSIBILITY_SETTINGS;
        if(any(s,"безпек","security"))return Settings.ACTION_SECURITY_SETTINGS;
        if(any(s,"головні налаштування","відкрий налаштування","зайди в налаштування"))return Settings.ACTION_SETTINGS;
        return null;
    }

    private boolean any(String s,String...k){for(String x:k)if(s.contains(x))return true;return false;}
    private boolean scroll(boolean down){AccessibilityNodeInfo n=scrollable(getRootInActiveWindow());return n!=null&&n.performAction(down?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);}
    private AccessibilityNodeInfo scrollable(AccessibilityNodeInfo n){if(n==null)return null;if(n.isScrollable())return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo x=scrollable(n.getChild(i));if(x!=null)return x;}return null;}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo n,String q){
        if(n==null||TextUtils.isEmpty(q))return null;String z=q.toLowerCase(Locale.ROOT);CharSequence t=n.getText(),d=n.getContentDescription();
        if((t!=null&&t.toString().toLowerCase(Locale.ROOT).contains(z))||(d!=null&&d.toString().toLowerCase(Locale.ROOT).contains(z)))return n;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo x=find(n.getChild(i),q);if(x!=null)return x;}return null;
    }
    private AccessibilityNodeInfo clickable(AccessibilityNodeInfo n){AccessibilityNodeInfo x=n;for(int i=0;i<6&&x!=null;i++){if(x.isClickable())return x;x=x.getParent();}return null;}

    public String screenText(){
        AccessibilityNodeInfo r=getRootInActiveWindow();if(r==null)return "";
        Set<String> out=new LinkedHashSet<>();collect(r,out,0);List<String> x=new ArrayList<>(out);if(x.size()>30)x=x.subList(0,30);return String.join(" · ",x);
    }
    private void collect(AccessibilityNodeInfo n,Set<String> out,int d){
        if(n==null||d>24||out.size()>60)return;add(out,n.getText());add(out,n.getContentDescription());for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),out,d+1);
    }
    private void add(Set<String> out,CharSequence c){if(c==null)return;String s=c.toString().trim().replaceAll("\\s+"," ");if(!s.isEmpty()&&s.length()<=160)out.add(s);}
    private String translateUi(String s){
        String[][] m={{"settings","налаштування"},{"storage","пам’ять"},{"battery","батарея"},{"display","екран"},{"sound","звук"},{"notifications","сповіщення"},{"privacy","конфіденційність"},{"security","безпека"},{"network","мережа"},{"internet","інтернет"},{"apps","додатки"},{"permissions","дозволи"},{"camera","камера"},{"microphone","мікрофон"},{"files","файли"},{"download","завантажити"},{"delete","видалити"},{"cancel","скасувати"},{"save","зберегти"},{"open","відкрити"},{"close","закрити"},{"back","назад"},{"home","головна"},{"search","пошук"},{"update","оновити"}};
        String o=s;for(String[]p:m)o=o.replaceAll("(?i)\\b"+java.util.regex.Pattern.quote(p[0])+"\\b",p[1]);return o.equals(s)?"Словник v0.1 не знайшов знайомих UI-слів.":o;
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private TextView text(String s,int sp,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);return t;}

    private void showHandle(){
        if(wm==null||handle!=null)return;TextView h=text("N",12,Color.rgb(235,240,255));h.setGravity(Gravity.CENTER);h.setBackground(bg(Color.argb(225,28,35,48),14));h.setOnClickListener(v->togglePanel());
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(dp(28),dp(74),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.END|Gravity.CENTER_VERTICAL;
        handle=h;try{wm.addView(handle,p);}catch(Exception e){handle=null;}
    }
    private void togglePanel(){if(panel!=null){hidePanel();return;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(12),dp(14),dp(12));box.setBackground(bg(Color.rgb(17,21,27),22));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("NEXUS · OFFLINE",15,Color.rgb(230,235,255));top.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
        Button close=new Button(this);close.setText("×");close.setOnClickListener(v->hidePanel());top.addView(close,new LinearLayout.LayoutParams(dp(48),dp(40)));box.addView(top);
        EditText in=new EditText(this);in.setHint("Напиши команду…");in.setTextColor(Color.WHITE);in.setHintTextColor(Color.GRAY);in.setMaxLines(3);box.addView(in,new LinearLayout.LayoutParams(-1,dp(78)));
        LinearLayout a=new LinearLayout(this);Button run=new Button(this);run.setText("Виконати");Button scr=new Button(this);scr.setText("Екран");Button home=new Button(this);home.setText("⌂");
        a.addView(run,new LinearLayout.LayoutParams(0,dp(48),1));a.addView(scr,new LinearLayout.LayoutParams(0,dp(48),1));a.addView(home,new LinearLayout.LayoutParams(dp(56),dp(48)));box.addView(a);
        panelResult=text("Готовий. Наприклад: «відкрий пам’ять телефона».",13,Color.rgb(190,200,220));panelResult.setPadding(4,dp(8),4,4);box.addView(panelResult,new LinearLayout.LayoutParams(-1,dp(120)));
        run.setOnClickListener(v->executeCommand(in.getText().toString(),r->panelResult.setText(r)));scr.setOnClickListener(v->panelResult.setText(screenText().isEmpty()?"Не бачу доступного тексту.":screenText()));home.setOnClickListener(v->performGlobalAction(GLOBAL_ACTION_HOME));
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(dp(360),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);p.gravity=Gravity.END|Gravity.CENTER_VERTICAL;p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        panel=box;try{wm.addView(panel,p);}catch(Exception e){panel=null;}
    }
    private void hidePanel(){if(wm!=null&&panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}panel=null;panelResult=null;}
    private void showHighlight(Rect r){
        if(wm==null||r==null||r.isEmpty())return;if(highlight!=null)try{wm.removeView(highlight);}catch(Exception ignored){}
        View v=new View(this);GradientDrawable g=new GradientDrawable();g.setColor(Color.argb(35,159,183,255));g.setStroke(dp(3),Color.rgb(159,183,255));g.setCornerRadius(dp(8));v.setBackground(g);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.max(dp(8),r.width()),Math.max(dp(8),r.height()),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.START;p.x=r.left;p.y=r.top;
        highlight=v;try{wm.addView(v,p);v.postDelayed(()->{if(highlight==v){try{wm.removeView(v);}catch(Exception ignored){}highlight=null;}},2000);}catch(Exception e){highlight=null;}
    }
    private void removeOverlays(){if(wm==null)return;if(handle!=null)try{wm.removeView(handle);}catch(Exception ignored){}if(panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}if(highlight!=null)try{wm.removeView(highlight);}catch(Exception ignored){}handle=null;panel=null;highlight=null;}
}
