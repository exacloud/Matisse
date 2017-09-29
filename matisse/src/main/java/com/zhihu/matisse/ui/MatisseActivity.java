/*
 * Copyright 2017 Zhihu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zhihu.matisse.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.zhihu.matisse.R;
import com.zhihu.matisse.internal.entity.Album;
import com.zhihu.matisse.internal.entity.Item;
import com.zhihu.matisse.internal.entity.SelectionSpec;
import com.zhihu.matisse.internal.model.AlbumCollection;
import com.zhihu.matisse.internal.model.SelectedItemCollection;
import com.zhihu.matisse.internal.ui.AlbumPreviewActivity;
import com.zhihu.matisse.internal.ui.BasePreviewActivity;
import com.zhihu.matisse.internal.ui.MediaSelectionFragment;
import com.zhihu.matisse.internal.ui.adapter.AlbumMediaAdapter;
import com.zhihu.matisse.internal.ui.adapter.AlbumsAdapter;
import com.zhihu.matisse.internal.utils.MediaStoreCompat;
import com.zhihu.matisse.internal.utils.PathUtils;

import java.util.ArrayList;

/**
 * Main Activity to display albums and media content (images/videos) in each album
 * and also support media selecting operations.
 */
public class MatisseActivity extends AppCompatActivity implements
        AlbumCollection.AlbumCallbacks, AdapterView.OnItemClickListener,
        MediaSelectionFragment.SelectionProvider, View.OnClickListener,
        AlbumMediaAdapter.CheckStateListener, AlbumMediaAdapter.OnMediaClickListener,
        AlbumMediaAdapter.OnPhotoCapture {
    public static final String EXTRA_RESULT_SELECTION = "extra_result_selection";
    public static final String EXTRA_RESULT_SELECTION_PATH = "extra_result_selection_path";
    private static final int REQUEST_CODE_PREVIEW = 23;
    private static final int REQUEST_CODE_CAPTURE = 24;
    private static final int TRANS_ANIM_DURATION_IN_MILLS = 300;
    private final AlbumCollection mAlbumCollection = new AlbumCollection();
    private MediaStoreCompat mMediaStoreCompat;
    private SelectedItemCollection mSelectedCollection = new SelectedItemCollection(this);
    private SelectionSpec mSpec;
    private AlbumsAdapter mAlbumsAdapter;
    //暂时不需要预览功能 后面有需要再加上
    //private TextView mButtonPreview;
    private TextView mButtonApply;
    private View mContainer;
    private View mEmptyView;
    private TextView mSelectedAlbumView;
    private ViewGroup mAlbumListLayoutView;
    private ViewStub mAlbumListViewStub;
    private ListView mAlbumListView;
    private View mMaskView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // programmatically set theme before super.onCreate()
        mSpec = SelectionSpec.getInstance();
        setTheme(mSpec.themeId);
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        setContentView(R.layout.activity_matisse);

        if (mSpec.needOrientationRestriction()) {
            setRequestedOrientation(mSpec.orientation);
        }

        if (mSpec.capture) {
            mMediaStoreCompat = new MediaStoreCompat(this);
            if (mSpec.captureStrategy == null)
                throw new RuntimeException("Don't forget to set CaptureStrategy.");
            mMediaStoreCompat.setCaptureStrategy(mSpec.captureStrategy);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        setTitle(R.string.matisse_title);

//      mButtonPreview = (TextView) findViewById(R.id.button_preview);
//      mButtonPreview.setOnClickListener(this);
        mButtonApply = (TextView) findViewById(R.id.button_apply);
        mButtonApply.setOnClickListener(this);
        mContainer = findViewById(R.id.container);
        mEmptyView = findViewById(R.id.empty_view);
        mSelectedAlbumView = (TextView) findViewById(R.id.selected_album);
        mSelectedAlbumView.setOnClickListener(this);
        mAlbumListViewStub = (ViewStub) findViewById(R.id.album_list_stub);

        mSelectedCollection.onCreate(savedInstanceState);
        updateBottomToolbar();

        mAlbumsAdapter = new AlbumsAdapter(this, null, false);
        mAlbumCollection.onCreate(this, this);
        mAlbumCollection.onRestoreInstanceState(savedInstanceState);
        mAlbumCollection.loadAlbums();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mSelectedCollection.onSaveInstanceState(outState);
        mAlbumCollection.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAlbumCollection.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_matisse, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home || item.getItemId() == R.id.action_cancel) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        finish();
        overridePendingTransition(R.anim.matisse_in_alpha, R.anim.matisse_out_alpha);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK)
            return;

        if (requestCode == REQUEST_CODE_PREVIEW) {
            Bundle resultBundle = data.getBundleExtra(BasePreviewActivity.EXTRA_RESULT_BUNDLE);
            ArrayList<Item> selected = resultBundle.getParcelableArrayList(SelectedItemCollection
                    .STATE_SELECTION);
            int collectionType = resultBundle.getInt(SelectedItemCollection.STATE_COLLECTION_TYPE,
                    SelectedItemCollection.COLLECTION_UNDEFINED);
            if (data.getBooleanExtra(BasePreviewActivity.EXTRA_RESULT_APPLY, false)) {
                Intent result = new Intent();
                ArrayList<Uri> selectedUris = new ArrayList<>();
                ArrayList<String> selectedPaths = new ArrayList<>();
                if (selected != null) {
                    for (Item item : selected) {
                        selectedUris.add(item.getContentUri());
                        selectedPaths.add(PathUtils.getPath(this, item.getContentUri()));
                    }
                }
                result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
                result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
                setResult(RESULT_OK, result);
                finish();
            } else {
                mSelectedCollection.overwrite(selected, collectionType);
                Fragment mediaSelectionFragment = getSupportFragmentManager().findFragmentByTag(
                        MediaSelectionFragment.class.getSimpleName());
                if (mediaSelectionFragment instanceof MediaSelectionFragment) {
                    ((MediaSelectionFragment) mediaSelectionFragment).refreshMediaGrid();
                }
                updateBottomToolbar();
            }
        } else if (requestCode == REQUEST_CODE_CAPTURE) {
            // Just pass the data back to previous calling Activity.
            Uri contentUri = mMediaStoreCompat.getCurrentPhotoUri();
            String path = mMediaStoreCompat.getCurrentPhotoPath();
            ArrayList<Uri> selected = new ArrayList<>();
            selected.add(contentUri);
            ArrayList<String> selectedPath = new ArrayList<>();
            selectedPath.add(path);
            Intent result = new Intent();
            result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selected);
            result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPath);
            setResult(RESULT_OK, result);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                MatisseActivity.this.revokeUriPermission(contentUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent
                                .FLAG_GRANT_READ_URI_PERMISSION);
            finish();
        }
    }

    private void updateBottomToolbar() {
        int selectedCount = mSelectedCollection.count();
        mButtonApply.setText(getString(R.string.button_apply, selectedCount, SelectionSpec
                .getInstance().maxSelectable));
        mButtonApply.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
//        if (v.getId() == R.id.button_preview) {
//            Intent intent = new Intent(this, SelectedPreviewActivity.class);
//            intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection
//                    .getDataWithBundle());
//            startActivityForResult(intent, REQUEST_CODE_PREVIEW);
//        } else
        if (v.getId() == R.id.button_apply) {
            Intent result = new Intent();
            ArrayList<Uri> selectedUris = (ArrayList<Uri>) mSelectedCollection.asListOfUri();
            result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
            ArrayList<String> selectedPaths = (ArrayList<String>) mSelectedCollection
                    .asListOfString();
            result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
            setResult(RESULT_OK, result);
            finish();
        } else if (v.getId() == R.id.selected_album) {
            changeAlbumLayoutState();
        }
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        hideAlbumListView();

        updateSelectorState(parent, view, position);

        mAlbumsAdapter.setSelectedPosition(position);
        mAlbumCollection.setStateCurrentSelection(position);
        mAlbumsAdapter.getCursor().moveToPosition(position);
        Album album = Album.valueOf(mAlbumsAdapter.getCursor());
        if (album.isAll() && SelectionSpec.getInstance().capture) {
            album.addCaptureCount();
        }
        onAlbumSelected(album);
    }

    @Override
    public void onAlbumLoad(final Cursor cursor) {
        mAlbumsAdapter.swapCursor(cursor);
        // select default album.
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                cursor.moveToPosition(mAlbumCollection.getCurrentSelection());
                Album album = Album.valueOf(cursor);
                mSelectedAlbumView.setText(album.getDisplayName(MatisseActivity.this));

                if (album.isAll() && SelectionSpec.getInstance().capture) {
                    album.addCaptureCount();
                }
                onAlbumSelected(album);
            }
        });
    }

    @Override
    public void onAlbumReset() {
        mAlbumsAdapter.swapCursor(null);
    }

    private void onAlbumSelected(Album album) {
        if (album.isAll() && album.isEmpty()) {
            mContainer.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mContainer.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
            Fragment fragment = MediaSelectionFragment.newInstance(album);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, fragment, MediaSelectionFragment.class.getSimpleName())
                    .commitAllowingStateLoss();
        }

        mSelectedAlbumView.setText(album.getDisplayName(this));
    }

    @Override
    public void onUpdate() {
        // notify bottom toolbar that check state changed.
        updateBottomToolbar();
    }

    @Override
    public void onMediaClick(Album album, Item item, int adapterPosition) {
        Intent intent = new Intent(this, AlbumPreviewActivity.class);
        intent.putExtra(AlbumPreviewActivity.EXTRA_ALBUM, album);
        intent.putExtra(AlbumPreviewActivity.EXTRA_ITEM, item);
        intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection
                .getDataWithBundle());
        startActivityForResult(intent, REQUEST_CODE_PREVIEW);
    }

    @Override
    public SelectedItemCollection provideSelectedItemCollection() {
        return mSelectedCollection;
    }

    @Override
    public void capture() {
        if (mMediaStoreCompat != null) {
            mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_CAPTURE);
        }
    }

    private void showAlbumListView() {
        if (mAlbumListLayoutView == null) {
            mAlbumListLayoutView = (ViewGroup) mAlbumListViewStub.inflate();
            mAlbumListView = (ListView) mAlbumListLayoutView.findViewById(R.id.album_list);
            mMaskView = mAlbumListLayoutView.findViewById(R.id.mask);
            mAlbumListView.setOnItemClickListener(this);
            mAlbumListView.setAdapter(mAlbumsAdapter);

            mMaskView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    hideAlbumListView();
                }
            });
        }

        mMaskView.setVisibility(View.VISIBLE);
        mAlbumListView.setVisibility(View.VISIBLE);

        TranslateAnimation translateInAnimation = new TranslateAnimation(Animation
                .RELATIVE_TO_SELF, 0.0F, Animation.RELATIVE_TO_SELF, 0.0F,
                Animation.RELATIVE_TO_SELF, 1.0F, Animation.RELATIVE_TO_SELF, 0.0F);
        translateInAnimation.setDuration(TRANS_ANIM_DURATION_IN_MILLS);
        mAlbumListView.startAnimation(translateInAnimation);
    }

    private void hideAlbumListView() {
        if (mAlbumListLayoutView != null) {
            mMaskView.setVisibility(View.INVISIBLE);
            mAlbumListView.setVisibility(View.INVISIBLE);

            TranslateAnimation translateOutAnimation = new TranslateAnimation(Animation
                    .RELATIVE_TO_SELF, 0.0F, Animation.RELATIVE_TO_SELF, 0.0F,
                    Animation.RELATIVE_TO_SELF, 0.0F, Animation.RELATIVE_TO_SELF, 1.0F);
            translateOutAnimation.setDuration(TRANS_ANIM_DURATION_IN_MILLS);
            mAlbumListView.startAnimation(translateOutAnimation);
        }
    }

    private boolean isAlbumListShow() {
        return mAlbumListView != null && mAlbumListView.getVisibility() == View.VISIBLE;
    }

    private void changeAlbumLayoutState() {
        if (isAlbumListShow()) {
            hideAlbumListView();
        } else {
            showAlbumListView();
        }
    }

    private void updateSelectorState(final AdapterView<?> parent, final View view, final int
            position) {
        int preSelectorPosition = mAlbumsAdapter.getSelectedPosition();
        if (preSelectorPosition == position) {
            return;
        }

        int firstVisiblePosition = parent.getFirstVisiblePosition();
        int lastVisiblePosition = parent.getLastVisiblePosition();
        if (position >= firstVisiblePosition && position <= lastVisiblePosition) {
            View currentSelector = view.findViewById(R.id.selector);
            currentSelector.setVisibility(View.VISIBLE);
        }

        if (preSelectorPosition >= firstVisiblePosition && preSelectorPosition <=
                lastVisiblePosition) {
            View preSelector = parent.getChildAt(preSelectorPosition - firstVisiblePosition)
                    .findViewById(R.id.selector);
            preSelector.setVisibility(View.INVISIBLE);
        }
    }
}
