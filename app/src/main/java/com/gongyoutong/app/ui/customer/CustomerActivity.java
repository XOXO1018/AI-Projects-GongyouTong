package com.gongyoutong.app.ui.customer;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.gongyoutong.app.R;
import com.gongyoutong.app.data.Customer;
import com.gongyoutong.app.data.CustomerAdapter;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.CustomerDao;
import com.gongyoutong.app.database.CustomerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 客户管理页面
 */
public class CustomerActivity extends AppCompatActivity {

    private RecyclerView rvCustomers;
    private CustomerAdapter adapter;
    private CustomerDao customerDao;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText etSearch;
    private List<Customer> allCustomers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);

        customerDao = AppDatabase.getInstance(this).customerDao();

        initViews();
        setupToolbar();
        setupSearch();
        loadCustomers();
    }

    private void initViews() {
        rvCustomers = findViewById(R.id.rvCustomers);
        etSearch = findViewById(R.id.etSearch);
        FloatingActionButton fabAdd = findViewById(R.id.fabAddCustomer);

        rvCustomers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CustomerAdapter();
        adapter.setOnItemClickListener(customer -> {
            Toast.makeText(this, "查看客户: " + customer.getName(), Toast.LENGTH_SHORT).show();
        });
        rvCustomers.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showAddCustomerDialog());
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterCustomers(s.toString());
            }
        });
    }

    private void loadCustomers() {
        executor.execute(() -> {
            List<CustomerEntity> entities = customerDao.getAll();
            allCustomers.clear();
            for (CustomerEntity e : entities) {
                Customer c = new Customer();
                c.setId(e.getId());
                c.setName(e.getName());
                c.setPhone(e.getPhone());
                c.setAddress(e.getAddress());
                c.setTag(e.getTag());
                c.setServiceCount(e.getServiceCount());
                c.setTotalSpent(e.getTotalSpent());
                allCustomers.add(c);
            }
            runOnUiThread(() -> adapter.setList(new ArrayList<>(allCustomers)));
        });
    }

    private void filterCustomers(String query) {
        List<Customer> filtered = new ArrayList<>();
        for (Customer c : allCustomers) {
            if ((c.getName() != null && c.getName().contains(query)) ||
                (c.getPhone() != null && c.getPhone().contains(query))) {
                filtered.add(c);
            }
        }
        adapter.setList(filtered);
    }

    private void showAddCustomerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("添加客户");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        EditText etName = new EditText(this);
        etName.setHint("客户姓名");
        layout.addView(etName);

        EditText etPhone = new EditText(this);
        etPhone.setHint("联系电话");
        etPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        android.view.ViewGroup.MarginLayoutParams lp = new android.view.ViewGroup.MarginLayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        etPhone.setLayoutParams(lp);
        layout.addView(etPhone);

        EditText etAddress = new EditText(this);
        etAddress.setHint("地址");
        android.view.ViewGroup.MarginLayoutParams lp2 = new android.view.ViewGroup.MarginLayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        etAddress.setLayoutParams(lp2);
        layout.addView(etAddress);

        builder.setView(layout);
        builder.setPositiveButton("保存", (d, w) -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String address = etAddress.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, "请输入客户姓名", Toast.LENGTH_SHORT).show();
                return;
            }

            executor.execute(() -> {
                CustomerEntity entity = new CustomerEntity();
                entity.setId(String.valueOf(System.currentTimeMillis()));
                entity.setName(name);
                entity.setPhone(phone);
                entity.setAddress(address);
                entity.setCreatedAt(System.currentTimeMillis());
                entity.setUpdatedAt(System.currentTimeMillis());
                customerDao.insert(entity);

                runOnUiThread(() -> {
                    Toast.makeText(this, "客户添加成功", Toast.LENGTH_SHORT).show();
                    loadCustomers();
                });
            });
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
