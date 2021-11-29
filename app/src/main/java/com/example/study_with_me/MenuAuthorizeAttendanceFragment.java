package com.example.study_with_me;

import static java.lang.Math.*;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.ListFragment;

import com.example.study_with_me.activity.AlarmActivity;
import com.example.study_with_me.activity.AttendanceRegisterActivity;
import com.example.study_with_me.activity.CheckingAttendanceActivity;
import com.example.study_with_me.activity.MainActivity;
import com.example.study_with_me.activity.StudySearchActivity;
import com.example.study_with_me.adapter.AttendanceAdapter;
import com.example.study_with_me.adapter.SchedulerAdapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

public class MenuAuthorizeAttendanceFragment extends ListFragment {
    private MainActivity activity;
    private ListView listView;
    private Map<String, Object> studyInfo;
    private String studyGroupID;
    private ArrayList<Map<String, Object>> memberList = new ArrayList<>();
    private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private DatabaseReference databaseReference = firebaseDatabase.getReference();
    private DatabaseReference userRef = databaseReference.child("users");
    private String memberName;
    private TextView attendTime, attendGPS, attendPlace, attendRange;
    private Map<String, Object> attendInfo;
    private ImageView attendEditButton;
    private Button attendButton;
    private FirebaseAuth firebaseAuth;
    private String userID;
    private Boolean isSet, attend;
    private Date currentTime, registerTime;
    private String hour, minute;
    private long TIME_RANGE = 600000; // 출석 인정 범위(전후 10분, 밀리초)
    private TimeZone timeZone;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.attendance_authorize, container, false);

        // 상단 액션바 설정
        ActionBar actionBar = ((MainActivity)getActivity()).getSupportActionBar();
        actionBar.setTitle("출석 인증");
        actionBar.setDisplayHomeAsUpEnabled(true);

        firebaseAuth = FirebaseAuth.getInstance();
        userID = firebaseAuth.getCurrentUser().getUid();

        activity = (MainActivity) getActivity();
        studyInfo = activity.getStudyInfo();
        studyGroupID = (String) studyInfo.get("studyGroupID");

        attendTime = root.findViewById(R.id.attendTime);
        attendGPS = root.findViewById(R.id.attendGPS);
        attendPlace = root.findViewById(R.id.attendPlace);
        attendRange = root.findViewById(R.id.attendRange);
        attendEditButton = root.findViewById(R.id.AttendEditButton);
        attendButton = root.findViewById(R.id.attendButton);

        timeZone = TimeZone.getTimeZone("Asia/Seoul");
        initializeAttendButton();

        listView = (ListView)root.findViewById(android.R.id.list);
        View headerView = LayoutInflater.from(getActivity()).inflate(R.layout.member_attendance_title, null);
        listView.addHeaderView(headerView);

        setMyAttendance();
        getMemberList();

        attendEditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), AttendanceRegisterActivity.class);
                intent.putExtra("attendInfo", (Serializable)attendInfo);
                intent.putExtra("studyGroupID", studyGroupID);
                view.getContext().startActivity(intent);
            }
        });

        attendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isSet) { // 등록하기 버튼
                    Intent intent = new Intent(view.getContext(), AttendanceRegisterActivity.class);
                    intent.putExtra("attendInfo", (Serializable) attendInfo);
                    intent.putExtra("studyGroupID", studyGroupID);
                    view.getContext().startActivity(intent);
                } else { // 출석하기 버튼
                    Intent intent = new Intent(view.getContext(), CheckingAttendanceActivity.class);
                    intent.putExtra("x", attendInfo.get("x").toString());
                    intent.putExtra("y", attendInfo.get("y").toString());
                    intent.putExtra("range", attendInfo.get("range").toString());
                    view.getContext().startActivity(intent);
                }
            }
        });

        return root;
    }

    // 만약 이미 등록된 상태라면(isSet = true) [등록하기] => [출석하기]
    private void initializeAttendButton() {
        userRef.child(userID).child("attendance").child(studyGroupID).get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                isSet = task.getResult().child("isSet").getValue(Boolean.class);
                attend = task.getResult().child("attend").getValue(Boolean.class);

                SimpleDateFormat dateFormat = new SimpleDateFormat("HHmm", Locale.KOREAN);
                dateFormat.setTimeZone(timeZone); // 서울로 표준시 설정
                hour = task.getResult().child("hour").getValue(String.class);
                minute = task.getResult().child("minute").getValue(String.class);
                try {
                    String currTimeStr = dateFormat.format(Calendar.getInstance(Locale.KOREAN).getTime());
                    currentTime = dateFormat.parse(currTimeStr);

                    String regTimeStr = hour + minute;
                    registerTime = dateFormat.parse(regTimeStr);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                /* 출석 계획 등록을 한 경우 */
                if (isSet) { attendButton.setText("출석하기"); }

                if (attend)
                {
                    attendButton.setBackgroundColor(Color.GRAY);
                    attendButton.setClickable(false);
                    attendButton.setText("출석 완료");
                }
                else if (abs(currentTime.getTime() - registerTime.getTime()) <= TIME_RANGE)
                {
                    // 출석 아직 안하고 출석 가능 시간대인 경우
                    attendButton.setClickable(true);
                }
                else
                {
                    attendButton.setBackgroundColor(Color.GRAY);
                    attendButton.setClickable(false);
                    attendButton.setText("출석 시간이 아닙니다.");
                }
            }
        });
    }


    private void setMyAttendance() {
        userRef.child(userID).child("attendance").child(studyGroupID)
                .get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                attendInfo = (Map<String, Object>) task.getResult().getValue();
                String hour = (String) attendInfo.get("hour");
                String minute = (String) attendInfo.get("minute");
                String x = (String) attendInfo.get("x");
                String y = (String) attendInfo.get("y");
                String place = (String) attendInfo.get("place");
                String range = (String) attendInfo.get("range");
                attendTime.setText(String.format("%s:%s", hour, minute));
                attendGPS.setText(x + ", " + y);
                attendPlace.setText(place);
                attendRange.setText(range + "m");
            }
        });
    }

    private void getMemberList() {
        memberList.clear();
        Map<String, String> membersMap = (Map<String, String>) studyInfo.get("memberList");
        for (Map.Entry<String, String> entry : membersMap.entrySet()) {
            final String memberID = entry.getValue();
            userRef.child(memberID).child("username").get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DataSnapshot> task) {
                    memberName = (String) task.getResult().getValue();
                }
            });

            Map<String, Object> member = new HashMap<>();

            userRef.child(memberID).child("attendance").child(studyGroupID)
                    .get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DataSnapshot> task) {
                    Boolean isSet = (Boolean) task.getResult().child("isSet").getValue();
                    Boolean attend = (Boolean) task.getResult().child("attend").getValue();
                    String place = (String) task.getResult().child("place").getValue();
                    String range = (String) task.getResult().child("range").getValue();
                    String hour = (String) task.getResult().child("hour").getValue();
                    String minute = (String) task.getResult().child("minute").getValue();
                    member.put("memberName", memberName);
                    member.put("isSet", isSet);
                    member.put("attend", attend);
                    member.put("place", place);
                    member.put("range", range);
                    member.put("time", String.format("%s:%s", hour, minute));
                    memberList.add(member);
                    Log.d("memberList", memberList.toString());
                    setListView();
                }
            });
        }
    }

    private void setListView() {
        final AttendanceAdapter attendanceAdapter = new AttendanceAdapter(getActivity(), memberList);
        listView.setAdapter(attendanceAdapter);
    }
}