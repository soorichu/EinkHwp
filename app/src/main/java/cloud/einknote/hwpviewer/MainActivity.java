package cloud.einknote.hwpviewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RadioGroup rgStorage;
    private RadioButton rbInternal, rbExternal;
    private Button btnBrowseFolder, btnSelectFont;
    private TextView tvCurrentPath, tvSelectedFont, tvEmptyMessage;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;

    private final List<HwpDbHelper.HwpItem> displayList = new ArrayList<>();
    private FileAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private File currentDirectory;
    private File internalRoot;
    private File externalRoot;

    private HwpDbHelper dbHelper;

    private static final String PREFS_NAME = "EinkHwpPrefs";
    private static final String KEY_FONT_PATH = "custom_font_path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new HwpDbHelper(this);

        rgStorage = findViewById(R.id.rgStorage);
        rbInternal = findViewById(R.id.rbInternal);
        rbExternal = findViewById(R.id.rbExternal);
        btnBrowseFolder = findViewById(R.id.btnBrowseFolder);
        btnSelectFont = findViewById(R.id.btnSelectFont);
        tvCurrentPath = findViewById(R.id.tvCurrentPath);
        tvSelectedFont = findViewById(R.id.tvSelectedFont);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        progressBar = findViewById(R.id.progressBar);
        recyclerView = findViewById(R.id.recyclerView);

        adapter = new FileAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        initStorageRoots();
        loadSavedFontInfo();
        setupListeners();
        checkPermissions();
    }

    private void initStorageRoots() {
        internalRoot = findValidInternalRoot();
        externalRoot = findValidExternalSdCard();

        if (externalRoot == null) {
            rbExternal.setEnabled(false);
            rbExternal.setText("외부 SD카드 (없음)");
        } else {
            rbExternal.setEnabled(true);
            rbExternal.setText("외부 SD카드");
        }

        rbInternal.setChecked(true);
        currentDirectory = internalRoot;
        tvCurrentPath.setText(currentDirectory.getAbsolutePath());
    }

    private File findValidInternalRoot() {
        File standardDir = Environment.getExternalStorageDirectory();
        if (isValidDirectory(standardDir)) return standardDir;

        File[] appDirs = ContextCompat.getExternalFilesDirs(this, null);
        if (appDirs != null && appDirs.length > 0 && appDirs[0] != null) {
            File root = appDirs[0];
            while (root.getParentFile() != null && root.getParentFile().canRead()
                    && !root.getParentFile().getAbsolutePath().equals("/storage")
                    && !root.getParentFile().getAbsolutePath().equals("/")) {
                root = root.getParentFile();
            }
            if (isValidDirectory(root)) return root;
        }

        String[] fallbackPaths = { "/storage/emulated/0", "/sdcard", "/mnt/sdcard", "/storage/sdcard0" };
        for (String path : fallbackPaths) {
            File f = new File(path);
            if (isValidDirectory(f)) return f;
        }
        return Environment.getExternalStorageDirectory();
    }

    private File findValidExternalSdCard() {
        File[] dirs = ContextCompat.getExternalFilesDirs(this, null);
        if (dirs == null || dirs.length <= 1) return null;

        for (int i = 1; i < dirs.length; i++) {
            File d = dirs[i];
            if (d != null) {
                try {
                    if (Environment.isExternalStorageRemovable(d)) {
                        File root = d;
                        while (root.getParentFile() != null && root.getParentFile().canRead()
                                && !root.getParentFile().getAbsolutePath().equals("/storage")
                                && !root.getParentFile().getAbsolutePath().equals("/")) {
                            root = root.getParentFile();
                        }
                        if (isValidDirectory(root) && !root.getAbsolutePath().equals(internalRoot.getAbsolutePath())) {
                            return root;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private boolean isValidDirectory(File dir) {
        return dir != null && dir.exists() && dir.isDirectory() && dir.canRead();
    }

    private void loadSavedFontInfo() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedFont = prefs.getString(KEY_FONT_PATH, null);
        if (savedFont != null && new File(savedFont).exists()) {
            tvSelectedFont.setText("폰트: " + new File(savedFont).getName());
        } else {
            tvSelectedFont.setText("폰트: 기본 시스템 폰트");
        }
    }

    private void setupListeners() {
        rgStorage.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbInternal) {
                currentDirectory = internalRoot;
            } else if (checkedId == R.id.rbExternal) {
                if (externalRoot != null && externalRoot.exists()) {
                    currentDirectory = externalRoot;
                } else {
                    Toast.makeText(this, "연결된 외부 SD카드를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    rbInternal.setChecked(true);
                    return;
                }
            }
            tvCurrentPath.setText(currentDirectory.getAbsolutePath());
        });

        btnBrowseFolder.setOnClickListener(v -> showFolderBrowserDialog());
        btnSelectFont.setOnClickListener(v -> showFontBrowserDialog());
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                loadDbFileList();
            }
        } else {
            loadDbFileList();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 뷰어에서 메인으로 돌아왔을 때 최신 DB 상태로 화면 갱신
        loadDbFileList();
    }

    // 항상 DB에 남아있는 파일 목록을 조회해 메인에 표시
    private void loadDbFileList() {
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<HwpDbHelper.HwpItem> list = dbHelper.getAllHistory();
            runOnUiThread(() -> {
                displayList.clear();
                displayList.addAll(list);
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);

                if (displayList.isEmpty()) {
                    tvEmptyMessage.setText("* 외부 저장공간 오류로 files 폴더만 보이는 경우, Android/data/cloud.einknote.hwpviewer 폴더에 hwp와 ttf 파일을 넣어보세요.");
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                } else {
                    tvEmptyMessage.setVisibility(View.GONE);
                }
            });
        });
    }

    // 파일 열기 공통 처리 (존재 여부 검증 및 DB 자동 삭제/갱신)
    private void openHwpFile(File file) {
        if (!file.exists()) {
            // 파일이 없으면 DB에서 삭제
            executor.execute(() -> {
                dbHelper.deleteByPath(file.getAbsolutePath());
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "파일을 찾을 수 없어 목록에서 삭제했습니다:\n" + file.getName(), Toast.LENGTH_SHORT).show();
                    loadDbFileList();
                });
            });
            return;
        }

        // 파일이 존재하면 DB 타임스탬프 갱신
        executor.execute(() -> dbHelper.upsertHwpFile(file.getName(), file.getAbsolutePath()));

        Intent intent = new Intent(MainActivity.this, HwpViewerActivity.class);
        intent.putExtra("FILE_PATH", file.getAbsolutePath());
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        intent.putExtra("FONT_PATH", prefs.getString(KEY_FONT_PATH, null));
        startActivity(intent);
    }

    private void showFolderBrowserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_folder_browser, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextView tvDialogPath = dialogView.findViewById(R.id.tvDialogCurrentPath);
        Button btnUp = dialogView.findViewById(R.id.btnUpFolder);
        ListView lvFolders = dialogView.findViewById(R.id.lvFolders);
        Button btnSelect = dialogView.findViewById(R.id.btnSelectCurrentFolder);

        final File[] tempBrowseDir = { currentDirectory };
        final File[] selectedTarget = { currentDirectory };
        final List<File> itemList = new ArrayList<>();
        final List<String> itemNames = new ArrayList<>();

        ArrayAdapter<String> folderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, itemNames);
        lvFolders.setAdapter(folderAdapter);

        Runnable refreshDialogList = () -> {
            tvDialogPath.setText("위치: " + tempBrowseDir[0].getAbsolutePath());
            itemList.clear();
            itemNames.clear();
            selectedTarget[0] = tempBrowseDir[0];
            btnSelect.setText("현재 폴더 파일 전체 등록");

            File[] files = tempBrowseDir[0].listFiles();
            if (files != null) {
                List<File> dirList = new ArrayList<>();
                List<File> hwpList = new ArrayList<>();

                for (File f : files) {
                    if (f.isDirectory() && !f.getName().startsWith(".")) {
                        dirList.add(f);
                    } else if (f.isFile() && f.getName().toLowerCase().endsWith(".hwp")) {
                        hwpList.add(f);
                    }
                }

                Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(hwpList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                for (File d : dirList) {
                    itemList.add(d);
                    itemNames.add("📁 " + d.getName());
                }
                for (File h : hwpList) {
                    itemList.add(h);
                    itemNames.add("📄 " + h.getName());
                }
            }
            folderAdapter.notifyDataSetChanged();
        };

        refreshDialogList.run();

        lvFolders.setOnItemClickListener((parent, view, position, id) -> {
            File clickedItem = itemList.get(position);
            if (clickedItem.isDirectory()) {
                tempBrowseDir[0] = clickedItem;
                refreshDialogList.run();
            } else if (clickedItem.isFile()) {
                selectedTarget[0] = clickedItem;
                btnSelect.setText("선택한 파일 열기 (" + clickedItem.getName() + ")");
            }
        });

        btnUp.setOnClickListener(v -> {
            File parent = tempBrowseDir[0].getParentFile();
            File currentRoot = rbInternal.isChecked() ? internalRoot : externalRoot;
            if (parent != null && parent.canRead() && !tempBrowseDir[0].equals(currentRoot)) {
                tempBrowseDir[0] = parent;
                refreshDialogList.run();
            } else {
                Toast.makeText(MainActivity.this, "최상위 경로입니다.", Toast.LENGTH_SHORT).show();
            }
        });

        btnSelect.setOnClickListener(v -> {
            File target = selectedTarget[0];
            if (target.isDirectory()) {
                currentDirectory = target;
                tvCurrentPath.setText(currentDirectory.getAbsolutePath());
                // 폴더 내 모든 HWP를 DB에 일괄 등록
                executor.execute(() -> {
                    File[] fList = target.listFiles();
                    if (fList != null) {
                        for (File f : fList) {
                            if (f.isFile() && f.getName().toLowerCase().endsWith(".hwp")) {
                                dbHelper.upsertHwpFile(f.getName(), f.getAbsolutePath());
                            }
                        }
                    }
                    runOnUiThread(this::loadDbFileList);
                });
            } else if (target.isFile()) {
                currentDirectory = target.getParentFile();
                tvCurrentPath.setText(target.getAbsolutePath());
                openHwpFile(target);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showFontBrowserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_folder_browser, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextView tvDialogPath = dialogView.findViewById(R.id.tvDialogCurrentPath);
        Button btnUp = dialogView.findViewById(R.id.btnUpFolder);
        ListView lvFolders = dialogView.findViewById(R.id.lvFolders);
        Button btnSelect = dialogView.findViewById(R.id.btnSelectCurrentFolder);

        btnSelect.setText(".ttf 파일을 선택해 주세요");
        btnSelect.setEnabled(false);

        final File[] tempBrowseDir = { (internalRoot != null && internalRoot.exists()) ? internalRoot : Environment.getExternalStorageDirectory() };
        final File[] selectedTtf = { null };
        final List<File> itemList = new ArrayList<>();
        final List<String> itemNames = new ArrayList<>();

        ArrayAdapter<String> folderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, itemNames);
        lvFolders.setAdapter(folderAdapter);

        Runnable refreshDialogList = () -> {
            tvDialogPath.setText("폰트 탐색: " + tempBrowseDir[0].getAbsolutePath());
            itemList.clear();
            itemNames.clear();
            selectedTtf[0] = null;
            btnSelect.setText(".ttf 파일을 선택해 주세요");
            btnSelect.setEnabled(false);

            File[] files = tempBrowseDir[0].listFiles();
            if (files != null) {
                List<File> dirList = new ArrayList<>();
                List<File> ttfList = new ArrayList<>();

                for (File f : files) {
                    if (f.isDirectory() && !f.getName().startsWith(".")) {
                        dirList.add(f);
                    } else if (f.isFile() && f.getName().toLowerCase().endsWith(".ttf")) {
                        ttfList.add(f);
                    }
                }

                Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(ttfList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                for (File d : dirList) {
                    itemList.add(d);
                    itemNames.add("📁 " + d.getName());
                }
                for (File t : ttfList) {
                    itemList.add(t);
                    itemNames.add("🔤 " + t.getName());
                }
            }
            folderAdapter.notifyDataSetChanged();
        };

        refreshDialogList.run();

        lvFolders.setOnItemClickListener((parent, view, position, id) -> {
            File clickedItem = itemList.get(position);
            if (clickedItem.isDirectory()) {
                tempBrowseDir[0] = clickedItem;
                refreshDialogList.run();
            } else if (clickedItem.isFile()) {
                selectedTtf[0] = clickedItem;
                btnSelect.setText("이 폰트 적용 (" + clickedItem.getName() + ")");
                btnSelect.setEnabled(true);
            }
        });

        btnUp.setOnClickListener(v -> {
            File parent = tempBrowseDir[0].getParentFile();
            if (parent != null && parent.canRead() && !tempBrowseDir[0].equals(internalRoot)) {
                tempBrowseDir[0] = parent;
                refreshDialogList.run();
            } else {
                Toast.makeText(MainActivity.this, "최상위 경로입니다.", Toast.LENGTH_SHORT).show();
            }
        });

        btnSelect.setOnClickListener(v -> {
            if (selectedTtf[0] != null) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(KEY_FONT_PATH, selectedTtf[0].getAbsolutePath())
                        .apply();
                tvSelectedFont.setText("폰트: " + selectedTtf[0].getName());
                Toast.makeText(MainActivity.this, "사용자 폰트가 설정되었습니다.", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvFileName, tvFilePath;
            Button btnExport;

            ViewHolder(View v) {
                super(v);
                tvFileName = v.findViewById(R.id.tvFileName);
                tvFilePath = v.findViewById(R.id.tvFilePath);
                btnExport = v.findViewById(R.id.btnExportEpub);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hwp_file, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HwpDbHelper.HwpItem item = displayList.get(position);
            File file = item.toFile();

            holder.tvFileName.setText(item.name);
            String dateStr = dateFormat.format(new Date(item.timestamp));
            holder.tvFilePath.setText(item.path + "\n(최근 열람: " + dateStr + ")");

            // 항목 클릭 시 열기 (존재 검증 및 자동 삭제 로직 포함)
            holder.itemView.setOnClickListener(v -> openHwpFile(file));

            // EPUB 추출
            holder.btnExport.setOnClickListener(v -> {
                if (!file.exists()) {
                    openHwpFile(file); // 없으면 자동 삭제 유도
                    return;
                }
                File destEpub = new File(file.getParentFile(), file.getName().replaceAll("(?i)\\.hwp$", "") + ".epub");
                executor.execute(() -> {
                    try {
                        HwpConverter.exportToEpub(file, destEpub);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "EPUB 생성 완료:\n" + destEpub.getName(), Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "추출 실패: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }
    }
}