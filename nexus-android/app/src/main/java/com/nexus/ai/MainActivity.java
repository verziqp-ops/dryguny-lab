package com.nexus.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int VOICE_REQ=77;
    private EditText input;
    private TextView output,status;
    private TextToSpeech tts;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        tts=new TextToSpeech(this,this);
        setContentView(buildUi());
        handleIncoming(getIntent());
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIncoming(i);}
    @Override protected void onResume(){super.onResume();updateStatus();}
    @Override protected void onDestroy(){if(tts!=null)tts.shutdown();super.onDestroy();}

    private View buildUi(){
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); sc.setBackgroundColor(Color.rgb(11,13,16));
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(24),dp(20),dp(30));
        sc.addView(root,new ScrollView.LayoutParams(-1,-1));

        LinearLayout head=row();
        TextView title=tv("NEXUS AI",29,Color.rgb(238,241,250)); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));
        TextView chip=tv("OFFLINE",12,Color.rgb(199,211,255)); chip.setGravity(Gravity.CENTER); chip.setBackground(round(Color.rgb(28,36,50),18));
        head.addView(chip,new LinearLayout.LayoutParams(dp(90),dp(36))); root.addView(head);

        root.addView(tv("Локальний Android-агент · Pixel 7 Pro",14,Color.rgb(145,155,175)),lp(-1,dp(34)));
        status=tv("",13,Color.rgb(220,190,140)); status.setPadding(dp(12),dp(10),dp(12),dp(10)); status.setBackground(round(Color.rgb(20,25,32),16));
        root.addView(status,margin(-1,-2,0,8,0,14));

        LinearLayout controls=row();
        Button enable=btn("Screen Control"); enable.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button media=btn("Доступ до файлів"); media.setOnClickListener(v->requestMedia());
        controls.addView(enable,new LinearLayout.LayoutParams(0,dp(52),1)); controls.addView(media,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(controls,margin(-1,dp(52),0,0,0,18));

        TextView q=tv("QUICK ASK",12,Color.rgb(159,183,255));q.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);root.addView(q,lp(-1,dp(30)));
        input=new EditText(this); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.rgb(112,121,140));
        input.setHint("Наприклад: відкрий пам’ять телефона"); input.setTextSize(16); input.setMinLines(2);input.setMaxLines(4);
        input.setPadding(dp(14),dp(10),dp(14),dp(10));input.setBackground(round(Color.rgb(18,22,28),18));root.addView(input,lp(-1,dp(92)));

        LinearLayout actions=row();
        Button run=btn("Виконати");run.setOnClickListener(v->runCommand(input.getText().toString()));
        Button voice=btn("Голос");voice.setOnClickListener(v->voice());
        Button screen=btn("Екран");screen.setOnClickListener(v->runCommand("що на екрані"));
        actions.addView(run,new LinearLayout.LayoutParams(0,dp(54),1));actions.addView(voice,new LinearLayout.LayoutParams(0,dp(54),1));actions.addView(screen,new LinearLayout.LayoutParams(0,dp(54),1));
        root.addView(actions,margin(-1,dp(54),0,10,0,14));

        output=tv("Готовий. У застосунку немає INTERNET permission.",15,Color.rgb(210,217,232));
        output.setPadding(dp(14),dp(14),dp(14),dp(14));output.setBackground(round(Color.rgb(15,18,23),18));output.setTextIsSelectable(true);root.addView(output,lp(-1,-2));

        LinearLayout outActions=row();
        Button speak=btn("Озвучити");speak.setOnClickListener(v->speak(output.getText().toString()));
        Button clear=btn("Очистити");clear.setOnClickListener(v->{input.setText("");output.setText("Готовий.");});
        outActions.addView(speak,new LinearLayout.LayoutParams(0,dp(50),1));outActions.addView(clear,new LinearLayout.LayoutParams(0,dp(50),1));
        root.addView(outActions,margin(-1,dp(50),0,10,0,20));

        TextView quick=tv("ШВИДКІ ДІЇ",12,Color.rgb(159,183,255));quick.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);root.addView(quick,lp(-1,dp(32)));
        root.addView(card("Пам’ять телефона","Відкрити системне сховище",()->runCommand("відкрий пам’ять телефона")));
        root.addView(card("Screen Vision","Прочитати доступний текст екрана",()->runCommand("що на екрані")));
        root.addView(card("Live Translate","Локальний UI-словник v0.1",()->runCommand("переклади екран")));
        root.addView(card("Local Memory","Показати збережені нотатки",()->runCommand("що ти пам'ятаєш")));
        root.addView(card("MultiStep","Приклад кількох послідовних дій",()->runCommand("відкрий wifi і потім назад і потім відкрий батарею")));

        TextView brain=tv("LOCAL BRAIN",12,Color.rgb(159,183,255));brain.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);root.addView(brain,margin(-1,dp(32),0,18,0,0));
        TextView info=tv("Core v0.1 активний\nСистемні дії, екран, локальна пам’ять і простий пошук працюють без інтернету. Повноцінну LiteRT-LM модель додамо наступним оновленням.",14,Color.rgb(181,190,209));
        info.setPadding(dp(14),dp(14),dp(14),dp(14));info.setBackground(round(Color.rgb(18,22,28),18));root.addView(info,lp(-1,-2));
        return sc;
    }

    private View card(String a,String b,Runnable r){
        LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(14),dp(12),dp(14),dp(12));x.setBackground(round(Color.rgb(18,22,28),16));
        x.addView(tv(a,16,Color.rgb(235,238,247)),lp(-1,dp(28)));x.addView(tv(b,13,Color.rgb(132,142,162)),lp(-1,-2));x.setOnClickListener(v->r.run());
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(0,0,0,dp(9));wrap.addView(x,new LinearLayout.LayoutParams(-1,-2));return wrap;
    }

    private void runCommand(String c){
        if(c==null||c.trim().isEmpty())return; output.setText("Виконую локально…");
        NexusAccessibilityService s=NexusAccessibilityService.get();
        if(s!=null){s.executeCommand(c,r->runOnUiThread(()->output.setText(r)));return;}
        String low=c.toLowerCase(Locale.ROOT);String action=localSetting(low);
        if(action!=null){try{startActivity(new Intent(action));output.setText("Відкрив системний розділ. Увімкни Screen Control, щоб NEXUS міг читати й натискати елементи інших екранів.");}catch(Exception e){output.setText("Не вдалося відкрити розділ.");}}
        else output.setText(localBrain(c));
    }

    private String localSetting(String s){
        if(s.contains("пам'ять телефона")||s.contains("пам’ять телефона")||s.contains("память телефона")||s.contains("storage"))return Settings.ACTION_INTERNAL_STORAGE_SETTINGS;
        if(s.contains("wifi")||s.contains("wi-fi")||s.contains("вайфай"))return Settings.ACTION_WIFI_SETTINGS;
        if(s.contains("bluetooth")||s.contains("блютуз"))return Settings.ACTION_BLUETOOTH_SETTINGS;
        if(s.contains("батаре"))return Settings.ACTION_BATTERY_SAVER_SETTINGS;
        if(s.contains("налаштування")||s.contains("settings"))return Settings.ACTION_SETTINGS;
        return null;
    }

    private String localBrain(String c){
        String s=c.toLowerCase(Locale.ROOT).trim();
        if(s.startsWith("запам'ятай ")||s.startsWith("запам’ятай ")||s.startsWith("запамятай ")){
            String v=c.substring(c.indexOf(' ')+1).trim();getSharedPreferences("memory",MODE_PRIVATE).edit().putString("last",v).apply();return "Зберіг локально: "+v;
        }
        if(s.contains("що ти пам")){String v=getSharedPreferences("memory",MODE_PRIVATE).getString("last","");return v.isEmpty()?"Локальна пам’ять порожня.":"Пам’ятаю: "+v;}
        if(s.contains("що ти вмієш")||s.equals("допомога"))return "Можу офлайн: відкривати системні налаштування; читати доступний текст екрана; натискати елементи за назвою; скролити; назад/додому; підсвічувати знайдений елемент; виконувати прості MultiStep-команди; пам’ятати локальну нотатку.";
        return "Цей запит потребує повної локальної LLM. У v0.1 вже працюють Screen Control та системні дії. Спробуй «допомога».";
    }

    private void voice(){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},12);return;}
        try{
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"uk-UA");i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Скажи команду NEXUS");startActivityForResult(i,VOICE_REQ);
        }catch(Exception e){output.setText("Офлайн-розпізнавач голосу не встановлений. Текстові команди працюють.");}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==VOICE_REQ&&resultCode==RESULT_OK&&data!=null){ArrayList<String> r=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(r!=null&&!r.isEmpty()){input.setText(r.get(0));runCommand(r.get(0));}}
    }
    private void requestMedia(){
        if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_AUDIO},13);
        else Toast.makeText(this,"На старіших Android доступ задається через системний вибір файлів.",Toast.LENGTH_SHORT).show();
    }
    private void handleIncoming(Intent i){
        if(i!=null&&Intent.ACTION_PROCESS_TEXT.equals(i.getAction())){CharSequence t=i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);if(t!=null&&input!=null){input.setText("поясни цей текст: "+t);output.setText("Текст отримано локально через Android Smart Action.");}}
    }
    private void updateStatus(){
        if(status==null)return;boolean on=NexusAccessibilityService.get()!=null;
        status.setText(on?"Screen Control: УВІМКНЕНО · бічна ручка N активна":"Screen Control: ВИМКНЕНО · системні переходи працюють, але NEXUS не може читати чужий екран");
        status.setTextColor(on?Color.rgb(176,232,194):Color.rgb(226,194,145));
    }
    private void speak(String s){if(tts!=null)tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"nexus");}
    @Override public void onInit(int st){if(st==TextToSpeech.SUCCESS&&tts!=null)tts.setLanguage(new Locale("uk","UA"));}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView tv(String s,int sp,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(Color.rgb(230,234,244));b.setBackground(round(Color.rgb(30,37,48),14));return b;}
    private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);x.setGravity(Gravity.CENTER_VERTICAL);return x;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private LinearLayout.LayoutParams margin(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
}
