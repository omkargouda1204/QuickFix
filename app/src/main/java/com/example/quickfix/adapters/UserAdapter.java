package com.example.quickfix.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.R;
import com.example.quickfix.UserModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {

    public interface ActionListener {
        void onViewDetails(UserModel u);
        void onChangeRole(UserModel u);
        void onToggleVerify(UserModel u);
        void onToggleActive(UserModel u);
        void onDelete(UserModel u);
    }

    private final Context ctx;
    private final List<UserModel> master  = new ArrayList<>();   // full list from server
    private final List<UserModel> display = new ArrayList<>();   // filtered / sorted view
    private ActionListener listener;

    public UserAdapter(Context ctx) { this.ctx = ctx; }

    public void setListener(ActionListener l) { this.listener = l; }

    // ── Data ──────────────────────────────────────────────────────────────
    public void setData(List<UserModel> data) {
        master.clear();
        if (data != null) master.addAll(data);
        display.clear();
        display.addAll(master);
        notifyDataSetChanged();
    }

    public int getDisplayCount() { return display.size(); }

    // ── Filter + Sort ─────────────────────────────────────────────────────
    public void applyFilter(String query, String roleKey, String sortKey) {
        display.clear();
        String q = query != null ? query.trim().toLowerCase() : "";
        for (UserModel u : master) {
            // role filter
            boolean roleOk = true;
            if (roleKey != null) {
                switch (roleKey) {
                    case "user":       roleOk = u.isUser && !u.isServiceProvider && !u.isAdmin; break;
                    case "provider":   roleOk = u.isServiceProvider; break;
                    case "admin":      roleOk = u.isAdmin; break;
                    case "verified":   roleOk = u.isVerified; break;
                    case "unverified": roleOk = !u.isVerified; break;
                }
            }
            if (!roleOk) continue;
            // search filter
            if (!q.isEmpty()) {
                boolean match = (u.name  != null && u.name.toLowerCase().contains(q))
                             || (u.email != null && u.email.toLowerCase().contains(q))
                             || (u.phone != null && u.phone.contains(q));
                if (!match) continue;
            }
            display.add(u);
        }
        // sort
        if ("oldest".equals(sortKey)) {
            display.sort((a, b) -> safeStr(a.createdAt).compareTo(safeStr(b.createdAt)));
        } else if ("name_az".equals(sortKey)) {
            display.sort((a, b) -> safeStr(a.name).compareToIgnoreCase(safeStr(b.name)));
        }
        // default newest-first (already from server)
        notifyDataSetChanged();
    }

    private static String safeStr(String s) { return s != null ? s : ""; }

    // ── In-place update ───────────────────────────────────────────────────
    public void updateItem(UserModel updated) {
        for (int i = 0; i < master.size(); i++) {
            if (master.get(i).firebaseUid.equals(updated.firebaseUid)) {
                master.set(i, updated); break;
            }
        }
        for (int i = 0; i < display.size(); i++) {
            if (display.get(i).firebaseUid.equals(updated.firebaseUid)) {
                display.set(i, updated);
                notifyItemChanged(i);
                return;
            }
        }
    }

    // ── Remove item ───────────────────────────────────────────────────────
    public void removeItem(String uid) {
        master.removeIf(u -> u.firebaseUid.equals(uid));
        for (int i = 0; i < display.size(); i++) {
            if (display.get(i).firebaseUid.equals(uid)) {
                display.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    // ── RecyclerView boilerplate ──────────────────────────────────────────
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_user_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(display.get(position));
    }

    @Override
    public int getItemCount() { return display.size(); }

    // ── ViewHolder ────────────────────────────────────────────────────────
    class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView tvInitial, tvName, tvEmail, tvPhone;
        final TextView badgeRole, badgeVerified, badgeStatus, tvJoined;
        final MaterialButton btnDetails, btnRole, btnVerify, btnActive, btnDelete;

        VH(View v) {
            super(v);
            card         = v.findViewById(R.id.userCard);
            tvInitial    = v.findViewById(R.id.tvUserInitial);
            tvName       = v.findViewById(R.id.tvUserName);
            tvEmail      = v.findViewById(R.id.tvUserEmail);
            tvPhone      = v.findViewById(R.id.tvUserPhone);
            badgeRole    = v.findViewById(R.id.badgeRole);
            badgeVerified= v.findViewById(R.id.badgeVerified);
            badgeStatus  = v.findViewById(R.id.badgeStatus);
            tvJoined     = v.findViewById(R.id.tvJoined);
            btnDetails   = v.findViewById(R.id.btnDetails);
            btnRole      = v.findViewById(R.id.btnRole);
            btnVerify    = v.findViewById(R.id.btnVerify);
            btnActive    = v.findViewById(R.id.btnActive);
            btnDelete    = v.findViewById(R.id.btnDelete);
        }

        void bind(UserModel u) {
            // Avatar initial + tint
            String init = (u.name != null && !u.name.isEmpty())
                    ? String.valueOf(u.name.charAt(0)).toUpperCase() : "?";
            tvInitial.setText(init);
            tvInitial.getBackground().setTint(u.getRoleColor());

            // Info
            tvName.setText(u.name != null ? u.name : "Unknown");
            tvEmail.setText(u.email != null && !u.email.isEmpty() ? u.email : "No email");
            if (u.phone != null && !u.phone.isEmpty()) {
                tvPhone.setVisibility(View.VISIBLE);
                tvPhone.setText(u.phone);
            } else {
                tvPhone.setVisibility(View.GONE);
            }

            // Role badge
            badgeRole.setText(u.getRoleLabel());
            badgeRole.getBackground().setTint(u.getRoleColor());

            // Verified badge
            if (u.isVerified) {
                badgeVerified.setText("✓ Verified");
                badgeVerified.getBackground().setTint(0xFF388E3C);
                badgeVerified.setTextColor(Color.WHITE);
            } else {
                badgeVerified.setText("Unverified");
                badgeVerified.getBackground().setTint(0xFFB0BEC5);
                badgeVerified.setTextColor(Color.WHITE);
            }

            // Status badge
            if (u.isActive) {
                badgeStatus.setText("Active");
                badgeStatus.getBackground().setTint(0xFF43A047);
            } else {
                badgeStatus.setText("Suspended");
                badgeStatus.getBackground().setTint(0xFFD32F2F);
            }

            // Joined date (first 10 chars)
            String date = (u.createdAt != null && u.createdAt.length() >= 10)
                    ? u.createdAt.substring(0, 10) : u.createdAt;
            tvJoined.setText("Joined: " + (date != null ? date : "—"));

            // Card border = red if suspended
            card.setStrokeColor(u.isActive ? Color.TRANSPARENT : 0xFFD32F2F);
            card.setStrokeWidth(u.isActive ? 0 : 3);

            // Dynamic button labels
            btnVerify.setText(u.isVerified ? "Unverify" : "Verify");
            btnActive.setText(u.isActive   ? "Suspend"  : "Activate");

            // Listeners
            btnDetails.setOnClickListener(v -> { if (listener != null) listener.onViewDetails(u); });
            btnRole   .setOnClickListener(v -> { if (listener != null) listener.onChangeRole(u); });
            btnVerify .setOnClickListener(v -> { if (listener != null) listener.onToggleVerify(u); });
            btnActive .setOnClickListener(v -> { if (listener != null) listener.onToggleActive(u); });
            btnDelete .setOnClickListener(v -> { if (listener != null) listener.onDelete(u); });
        }
    }
}

