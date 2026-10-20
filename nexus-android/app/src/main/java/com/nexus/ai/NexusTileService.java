package com.nexus.ai;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class NexusTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        Tile t=getQsTile();
        if (t!=null) {
            t.setLabel("NEXUS");
            t.setState(NexusAccessibilityService.get()!=null ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.updateTile();
        }
    }
    @Override public void onClick() {
        super.onClick();
        Intent i=new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Build.VERSION.SDK_INT>=34) {
            PendingIntent p=PendingIntent.getActivity(this,100,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(p);
        } else {
            startActivityAndCollapse(i);
        }
    }
}
