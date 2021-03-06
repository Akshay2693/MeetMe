package win.aladhims.meetme;

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseIndexRecyclerAdapter;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.hdodenhof.circleimageview.CircleImageView;
import pub.devrel.easypermissions.EasyPermissions;
import win.aladhims.meetme.Model.User;
import win.aladhims.meetme.Utility.PlacesUtils;
import win.aladhims.meetme.Utility.PolylineUtils;
import win.aladhims.meetme.ViewHolder.FriendViewHolder;

public class ListFriendActivity extends BaseActivity implements GoogleApiClient.OnConnectionFailedListener {

    //final fields
    private static final String TAG = ListFriendActivity.class.getSimpleName();
    public static final String MYUIDEXTRAINTENT = "MYUID";
    public static final String FRIENDUID = "FRIENDUID";
    public static final String MEETID = "MEETID";
    String[] perms = new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.INTERNET};

    //Firebase Fields
    private FirebaseAuth mAuth;
    private FirebaseUser mUser;
    private DatabaseReference rootRef,meetRef,myFriendRef,userRef;
    private Query friendListRef;
    private FirebaseIndexRecyclerAdapter<User,FriendViewHolder> mAdapter;

    private GoogleApiClient mGoogleApiClient;

    private SearchView searchView;

    @BindView(R.id.rv_friend_list) RecyclerView mRecyclerView;
    @BindView(R.id.pg_friend_list) ProgressBar mProgressBar;
    @BindView(R.id.friend_toolbar) Toolbar toolbar;
    @BindView(R.id.ci_search_friend) CircleImageView mCiSearch;
    @BindView(R.id.tv_search_friend) TextView mTvSearch;
    @BindView(R.id.btn_search_friend) Button mBtnSearch;
    @BindView(R.id.iv_no_friends) ImageView mIvNoFriends;
    @BindView(R.id.fab_add_friend) FloatingActionButton mFabAddFriend;
    @BindView(R.id.iv_no_result) ImageView mIvNoResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_friend);
        ButterKnife.bind(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this,this)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build();
        setSupportActionBar(toolbar);
        setTitle("List Friend");
        mFabAddFriend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ListFriendActivity.this,AddFriendActivity.class));
            }
        });
        toolbar.setTitleTextColor(Color.WHITE);
        rootRef = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser();
        if(mUser == null){
            startActivity(new Intent(this,SignInActivity.class));
            finish();
        }else{
            Intent intent = new Intent(this,NotifyMeService.class);
            intent.putExtra(MYUIDEXTRAINTENT,mUser.getUid());
            startService(intent);
            myFriendRef = rootRef.child("friends").child(mUser.getUid());
            userRef = rootRef.child("users");
            mAdapter = new Adapter(User.class,R.layout.friend_item,FriendViewHolder.class,myFriendRef,userRef);
            myFriendRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if(!dataSnapshot.hasChildren()){
                        mProgressBar.setVisibility(View.INVISIBLE);
                        mIvNoFriends.setVisibility(View.VISIBLE);
                    }
                }
                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }

        friendListRef = rootRef.child("users");
        meetRef = rootRef.child("meet");

        LinearLayoutManager layoutManager = new LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);

        if(!EasyPermissions.hasPermissions(this, perms)){
            EasyPermissions.requestPermissions(this, "minta lokasinya!",101,perms);
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = getIntent();
        if(Intent.ACTION_SEARCH.equals(i.getAction())){
            String query = i.getStringExtra(SearchManager.QUERY);
            doQuery(query);
        }
        if(mGoogleApiClient != null){
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mGoogleApiClient.isConnected()){
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onBackPressed() {
        if(!searchView.isIconified()){
            if(mIvNoResult.getVisibility() == View.VISIBLE){
                mIvNoResult.setVisibility(View.INVISIBLE);
            }

            searchView.setIconified(true);
        }else {
            super.onBackPressed();
        }
    }

    private void doQuery(String query){
        mIvNoFriends.setVisibility(View.INVISIBLE);
        mFabAddFriend.setVisibility(View.INVISIBLE);
        final Query q = friendListRef.orderByChild("email").equalTo(query);
        Log.d(TAG,q.toString());
        mProgressBar.setVisibility(View.VISIBLE);
        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mRecyclerView.setVisibility(View.INVISIBLE);
                mProgressBar.setVisibility(View.INVISIBLE);
                if(dataSnapshot.getValue()!=null) {
                    mCiSearch.setVisibility(View.VISIBLE);
                    mBtnSearch.setVisibility(View.VISIBLE);
                    mTvSearch.setVisibility(View.VISIBLE);
                    User user = null;
                    for(final DataSnapshot dataSnapshot1 : dataSnapshot.getChildren()) {
                        user = dataSnapshot1.getValue(User.class);
                        final String username = user.getName();
                        myFriendRef.child(dataSnapshot1.getKey()).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                if(dataSnapshot.getValue()!=null){
                                    mBtnSearch.setText("MEET");
                                    mBtnSearch.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            doMeet(dataSnapshot1.getKey(),username);
                                        }
                                    });
                                }else{
                                    mBtnSearch.setText("ADD");
                                    mBtnSearch.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            Map<String, Object> updateAdd = new HashMap<>();
                                            updateAdd.put("/friends/"+"/"+mUser.getUid()+"/"+dataSnapshot1.getKey(),true);
                                            updateAdd.put("/friends/"+"/"+dataSnapshot1.getKey()+"/"+mUser.getUid(),true);
                                            rootRef.updateChildren(updateAdd)
                                                    .addOnCompleteListener(ListFriendActivity.this, new OnCompleteListener<Void>() {
                                                        @Override
                                                        public void onComplete(@NonNull Task<Void> task) {
                                                            mBtnSearch.setText("MEET");
                                                            mBtnSearch.setOnClickListener(new View.OnClickListener() {
                                                                @Override
                                                                public void onClick(View v) {
                                                                    doMeet(dataSnapshot1.getKey(),username);
                                                                }
                                                            });
                                                        }
                                                    });
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                    }
                    Glide.with(getApplicationContext())
                            .load(user.getPhotoURL())
                            .into(mCiSearch);
                    mTvSearch.setText(user.getName());

                }else{
                    mIvNoResult.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void doMeet(final String uid, final String friendName){
        final ProgressDialog progressDialog = makeRawProgressDialog("Menunggu Respon");
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "batal", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                rootRef.child("invite").child(uid).removeValue();
            }
        });
        progressDialog.show();
        final String meetActId = rootRef.push().getKey();
        Map<String, Object> collect = new HashMap<>();
        collect.put("inviter", mUser.getUid());
        collect.put("meetID", meetActId);
        collect.put("agree", false);
        Map<String, Object> up = new HashMap<>();
        up.put("/invite/" + uid, collect);
        rootRef.updateChildren(up);
        final CountDownTimer timer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                progressDialog.dismiss();
                rootRef.child("invite").child(uid).removeValue();
                Toast.makeText(getApplicationContext(), friendName + " tidak merespon", Toast.LENGTH_LONG).show();
            }
        };
        timer.start();
        rootRef.child("invite").child(uid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    boolean b = (Boolean) dataSnapshot.child("agree").getValue();
                    if (b) {
                        timer.cancel();
                        progressDialog.dismiss();
                        Intent i = new Intent(getApplicationContext(), DirectMeActivity.class);
                        i.putExtra(MEETID, meetActId);
                        i.putExtra(FRIENDUID, uid);
                        startActivity(i);
                    }
                }else{
                    timer.cancel();
                    progressDialog.dismiss();
                    Toast.makeText(getApplicationContext(), friendName + " tidak menerima ajakan", Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.list_friend_menu,menu);

        final MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchManager searchManager = (SearchManager) getApplicationContext().getSystemService(SEARCH_SERVICE);

        if(searchItem != null){
            searchView = (SearchView) searchItem.getActionView();
        }
        if(searchView != null){
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        }
        ((EditText)searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text)).setHintTextColor(ContextCompat.getColor(this,android.R.color.white));
        searchView.setQueryHint("Email Teman");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                doQuery(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mCiSearch.setVisibility(View.INVISIBLE);
                mTvSearch.setVisibility(View.INVISIBLE);
                mBtnSearch.setVisibility(View.INVISIBLE);
                mIvNoResult.setVisibility(View.INVISIBLE);
                return true;
            }
        });
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if(mRecyclerView.getVisibility()==View.INVISIBLE){
                    mRecyclerView.setVisibility(View.VISIBLE);
                    mFabAddFriend.setVisibility(View.VISIBLE);
                    mCiSearch.setVisibility(View.INVISIBLE);
                    mTvSearch.setVisibility(View.INVISIBLE);
                    mBtnSearch.setVisibility(View.INVISIBLE);
                    mIvNoResult.setVisibility(View.INVISIBLE);
                    myFriendRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if(!dataSnapshot.hasChildren()){
                                mIvNoFriends.setVisibility(View.VISIBLE);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                }
                return true;
            }
        });

        searchView.startLayoutAnimation();
        ImageView closeButton = (ImageView) searchView.findViewById(android.support.v7.appcompat.R.id.search_close_btn);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchView.setQuery("",false);
                if(mRecyclerView.getVisibility()==View.INVISIBLE){
                    mRecyclerView.setVisibility(View.VISIBLE);
                    mFabAddFriend.setVisibility(View.VISIBLE);
                    mCiSearch.setVisibility(View.INVISIBLE);
                    mTvSearch.setVisibility(View.INVISIBLE);
                    mBtnSearch.setVisibility(View.INVISIBLE);
                    mIvNoResult.setVisibility(View.INVISIBLE);
                    myFriendRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if(!dataSnapshot.hasChildren()){
                                mIvNoFriends.setVisibility(View.VISIBLE);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                }
                searchItem.collapseActionView();
                searchView.clearFocus();
                searchView.setIconified(true);
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.signOut:
                Auth.GoogleSignInApi.signOut(mGoogleApiClient);
                mAuth.signOut();
                startActivity(new Intent(this,SignInActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    class Adapter extends FirebaseIndexRecyclerAdapter<User,FriendViewHolder> {

        public Adapter(Class<User> modelClass, @LayoutRes int modelLayout, Class<FriendViewHolder> viewHolderClass, Query keyRef, Query dataRef) {
            super(modelClass, modelLayout, viewHolderClass, keyRef, dataRef);
        }


        @Override
        protected void populateViewHolder(final FriendViewHolder holder, final User user, int position) {
            mProgressBar.setVisibility(View.GONE);
            if(getItemCount() > 0){
                mIvNoFriends.setVisibility(View.INVISIBLE);
            }

            final String uid = getRef(position).getKey();
            Glide.with(getApplicationContext())
                    .load(user.getPhotoURL())
                    .into(holder.mCiFriendPhoto);
            String[] name = user.getName().split(" ");
            holder.mTvFriendName.setText(name[0]);
            rootRef.child("users").child(uid).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if(dataSnapshot.child("LAT").getValue()!=null&&dataSnapshot.child("LONG").getValue()!=null){
                        double latitude = (Double) dataSnapshot.child("LAT").getValue();
                        double longitude = (Double) dataSnapshot.child("LONG").getValue();
                        LatLng latLng = new LatLng(latitude,longitude);
                        String request = PlacesUtils.requestPlace(latLng);
                        PolylineUtils.getResponse(getApplicationContext(), request, new PolylineUtils.VolleyCallback() {
                            @Override
                            public void onSuccess(String string) {
                                String place = PlacesUtils.getPlaces(string);
                                holder.mTvLastLoc.setText(place);
                            }
                        });
                    }else {
                        holder.mTvLastLoc.setText("Lokasi terakhir tidak diketahui");
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
            holder.mBtnMeetFriend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doMeet(uid,user.getName());
                }
            });
        }
    }
}
