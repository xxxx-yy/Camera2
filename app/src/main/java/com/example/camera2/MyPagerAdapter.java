package com.example.camera2;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.List;

public class MyPagerAdapter extends FragmentPagerAdapter {
    private List<Fragment> layoutList;

    public MyPagerAdapter(FragmentManager manager, List<Fragment> layoutList) {
        super(manager);
        this.layoutList = layoutList;
    }

    @Override
    public int getCount() {
        return layoutList.size();
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        return layoutList.get(position);
    }
}
