package com.nexus.ai;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class NexusTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        Tile tile=getQsTile();
        if(tile!=null){
            boolean active=ScreenCaptureService.Companion.isRunning();
            tile.setLabel(active ? "NEXUS Screen: ON" : "NEXUS Screen");
            tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    @Override public void onClick() {
        super.onClick();
        if(ScreenCaptureService.Companion.isRunning()){
            ScreenCaptureService.Companion.stop(this);
            Tile tile=getQsTile();
            if(tile!=null){
                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel("NEXUS Screen");
                tile.updateTile();
            }
            return;
        }

        Intent intent=new Intent(this, CapturePermissionActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if(Build.VERSION.SDK_INT>=34){
            PendingIntent pending=PendingIntent.getActivity(
                    this,100,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pending);
        }else{
            startActivityAndCollapse(intent);
        }
    }
}
