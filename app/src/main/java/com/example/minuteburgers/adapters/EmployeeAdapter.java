package com.example.minuteburgers.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minuteburgers.R;
import com.example.minuteburgers.models.User;

import java.util.List;

public class EmployeeAdapter extends RecyclerView.Adapter<EmployeeAdapter.EmployeeViewHolder> {
    private List<User> employees;
    private OnEmployeeActionListener listener;

    public interface OnEmployeeActionListener {
        void onEmployeeClick(User employee);
        void onEmployeeDelete(User employee);
    }

    public EmployeeAdapter(List<User> employees, OnEmployeeActionListener listener) {
        this.employees = employees;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EmployeeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_employee, parent, false);
        return new EmployeeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmployeeViewHolder holder, int position) {
        User employee = employees.get(position);
        holder.bind(employee, listener);
    }

    @Override
    public int getItemCount() {
        return employees.size();
    }

    static class EmployeeViewHolder extends RecyclerView.ViewHolder {
        private TextView nameTextView;
        private TextView emailTextView;
        private TextView branchTextView;
        private View editButton;
        private View deleteButton;

        public EmployeeViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.employeeName);
            emailTextView = itemView.findViewById(R.id.employeeEmail);
            branchTextView = itemView.findViewById(R.id.employeeBranch);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }

        public void bind(User employee, OnEmployeeActionListener listener) {
            nameTextView.setText(employee.getName());
            emailTextView.setText(employee.getEmail());
            branchTextView.setText("Branch: " + employee.getBranch());

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEmployeeClick(employee);
                }
            });

            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onEmployeeDelete(employee);
                    }
                });
            }
        }
    }
}
