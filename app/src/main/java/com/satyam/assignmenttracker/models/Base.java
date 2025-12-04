package com.satyam.assignmenttracker.models;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.satyam.assignmenttracker.Activities.ChatActivity;
import com.satyam.assignmenttracker.Activities.DrawingActivity;
import com.satyam.assignmenttracker.Activities.StudentCalendarActivity;
import com.satyam.assignmenttracker.R;

public class Base extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // You can actually remove these overrides entirely if you want,
    // but they’re harmless like this.
    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
    }

    /**
     * Quick tools bar (Sketch • AI • Calendar)
     * Call this in onCreate() of dashboards that extend Base.
     */
    protected void setupQuickToolsBar() {
        // Find the real root view of the Activity
        View content = findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;

        ViewGroup contentGroup = (ViewGroup) content;
        if (contentGroup.getChildCount() == 0) return;

        // This is usually your root ConstraintLayout (the one in your XML)
        View root = contentGroup.getChildAt(0);
        if (!(root instanceof androidx.constraintlayout.widget.ConstraintLayout)) {
            // If some screen doesn't use ConstraintLayout as root, skip
            return;
        }

        androidx.constraintlayout.widget.ConstraintLayout parent =
                (androidx.constraintlayout.widget.ConstraintLayout) root;

        // Inflate the bar into the parent
        View bar = getLayoutInflater().inflate(R.layout.layout_quick_tools_bar, parent, false);
        int barId = View.generateViewId();
        bar.setId(barId);
        parent.addView(bar);

        // Constrain it to the bottom, centered, with *small* margins
        androidx.constraintlayout.widget.ConstraintSet cs = new androidx.constraintlayout.widget.ConstraintSet();
        cs.clone(parent);
        cs.connect(barId, androidx.constraintlayout.widget.ConstraintSet.BOTTOM,
                androidx.constraintlayout.widget.ConstraintSet.PARENT_ID,
                androidx.constraintlayout.widget.ConstraintSet.BOTTOM, dp(16));   // ⬅ was 90
        cs.connect(barId, androidx.constraintlayout.widget.ConstraintSet.START,
                androidx.constraintlayout.widget.ConstraintSet.PARENT_ID,
                androidx.constraintlayout.widget.ConstraintSet.START, dp(16));
        cs.connect(barId, androidx.constraintlayout.widget.ConstraintSet.END,
                androidx.constraintlayout.widget.ConstraintSet.PARENT_ID,
                androidx.constraintlayout.widget.ConstraintSet.END, dp(16));
        cs.constrainHeight(barId, androidx.constraintlayout.widget.ConstraintSet.WRAP_CONTENT);
        cs.applyTo(parent);

        // ✅ IMPORTANT: add bottom padding to the ScrollView so content isn't hidden
        View main = findViewById(R.id.main); // your ScrollView id in dashboards
        if (main != null) {
            int extraBottom = dp(96); // space for bar + some breathing room
            main.setPadding(
                    main.getPaddingLeft(),
                    main.getPaddingTop(),
                    main.getPaddingRight(),
                    main.getPaddingBottom() + extraBottom
            );
        }

        // Wire up buttons
        View btnSketch = bar.findViewById(R.id.btnQuickSketch);
        View btnAi     = bar.findViewById(R.id.btnQuickAi);
        View btnCal    = bar.findViewById(R.id.btnQuickCalendar);

        if (btnSketch != null) {
            btnSketch.setOnClickListener(v ->
                    startActivity(new Intent(this, DrawingActivity.class)));
        }
        if (btnAi != null) {
            btnAi.setOnClickListener(v ->
                    startActivity(new Intent(this, ChatActivity.class)));
        }
        if (btnCal != null) {
            btnCal.setOnClickListener(v ->
                    startActivity(new Intent(this, StudentCalendarActivity.class)));
        }
    }


    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
