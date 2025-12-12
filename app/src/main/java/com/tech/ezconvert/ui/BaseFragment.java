package com.tech.ezconvert.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

public abstract class BaseFragment extends Fragment {
    
    protected NavController navController;
    protected Context context;
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
    }
    
    protected void navigateTo(int destinationId) {
        navController.navigate(destinationId);
    }
    
    protected void navigateTo(int destinationId, Bundle args) {
        navController.navigate(destinationId, args);
    }
    
    protected void navigateUp() {
        navController.navigateUp();
    }
}