package win.aladhims.meetme;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import win.aladhims.meetme.Model.User;

/**
 * Created by Aladhims on 21/03/2017.
 */

public class NotifyMeService extends IntentService {

    public static final int NOTIFYID = 1;
    public static final String AGREEEXTRA = "AGREEEXTRA";

    private DatabaseReference rootRef,checkRef;
    private Context context;
    private String friendID,friendName,meetID,userUID,friendPhotoURL ;
    private boolean agree;
    public NotifyMeService() {
        super("NotifyMeService");
    }

    @Override
    protected void onHandleIntent(@Nullable final Intent intent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                rootRef = FirebaseDatabase.getInstance().getReference();
                context = getApplicationContext();
                userUID = intent.getStringExtra(ListFriendActivity.MYUIDEXTRAINTENT);
                Log.d("SERVICES",userUID);
                checkRef = rootRef.child("invite").child(userUID);
                checkRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if(dataSnapshot.getValue() != null) {
                            friendID = (String) dataSnapshot.child("inviter").getValue();
                            Log.d("SERVICESFRIEND",friendID);
                            agree = (Boolean) dataSnapshot.child("agree").getValue();
                            meetID = (String) dataSnapshot.child("meetID").getValue();
                            rootRef.child("users").child(friendID).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    User user = dataSnapshot.getValue(User.class);
                                    friendName = user.getName();
                                    friendPhotoURL = user.getPhotoURL();
                                    if (!agree) {
                                        new AsyncTask<Void,Void,Bitmap>() {
                                            @Override
                                            protected Bitmap doInBackground(Void... params) {
                                                Log.d("SERVICESPHOTO",friendName + " dan photonya " + friendPhotoURL );
                                                return getBitmapFromURL(friendPhotoURL);
                                            }

                                            @Override
                                            protected void onPostExecute(Bitmap bitmap) {
                                                NotifyMe(friendName, friendID, meetID, bitmap);
                                            }
                                        }.execute();
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });

                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

            }
        }).run();
    }

    public Bitmap getCroppedBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    public Bitmap getBitmapFromURL(String strURL) {
        try {
            URL url = new URL(strURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void NotifyMe(String inviterName, String inviterId,String meetID,Bitmap largeIcon){

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setColor(ContextCompat.getColor(context,R.color.colorPrimary));
        builder.setAutoCancel(true)
                .setContentTitle(inviterName + " ingin ketemuan")
                .setContentText(inviterName + " ingin ketemuan dengan anda")
                .setSmallIcon(R.drawable.ic_photo_library);



        builder.setLargeIcon(getCroppedBitmap(largeIcon));


        Intent yesIntent = new Intent(context,DirectMeActivity.class);
        yesIntent.putExtra(AGREEEXTRA,true);
        yesIntent.putExtra(ListFriendActivity.FRIENDUID,inviterId)
                .putExtra(ListFriendActivity.MEETID,meetID);

        Intent NoIntent = new Intent(context,ButtonReceiverNotif.class);
        NoIntent.putExtra("notificationID",NOTIFYID);

        PendingIntent yesPendingIntent = PendingIntent.getActivity(context,0,yesIntent,PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(context,1,NoIntent,PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Action actionYes = new NotificationCompat.Action(R.drawable.ic_send,"Okey!",yesPendingIntent);
        NotificationCompat.Action actionNo = new NotificationCompat.Action(R.drawable.ic_photo_library,"Nggak!",dismissPendingIntent);
        builder.addAction(actionYes);
        builder.addAction(actionNo);
        builder.setPriority(Notification.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        manager.notify(NOTIFYID,builder.build());
    }
}
