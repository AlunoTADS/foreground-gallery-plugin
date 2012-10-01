package com.foregroundgalleryplugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

/**
 * Loads images from SD card. 
 * 
 * @author Mihai Fonoage
 *
 */
public class GalleryActivity extends Activity implements
OnItemClickListener {
    
    /**
     * Grid view holding the images.
     */
    private GridView sdcardImages;
    /**
     * Image adapter for the grid view.
     */
    private ImageAdapter imageAdapter;
    /**
     * Display used for getting the width of the screen. 
     */
    private Display display;
    
    private LoadImagesFromSDCard load;
    
    private Map<Integer, Integer> sequencialImageID;

    /**
     * Creates the content view, sets up the grid, the adapter, and the click listener.
     * 
     * @param savedInstanceState 
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        
        // Request progress bar
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.gallery);

        display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

        setupViews();
        setProgressBarIndeterminateVisibility(true); 
        loadImages();
    }

    /**
     * Free up bitmap related resources.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        final GridView grid = sdcardImages;
        final int count = grid.getChildCount();
        ImageView v = null;
        for (int i = 0; i < count; i++) {
            v = (ImageView) grid.getChildAt(i);
            ((BitmapDrawable) v.getDrawable()).setCallback(null);
        }
        if (load != null){
        	load.cancel(true);
        }
    }
    /**
     * Setup the grid view.
     */
    private void setupViews() {
        sdcardImages = (GridView) findViewById(R.id.sdcard);
        sdcardImages.setNumColumns(display.getWidth()/95);
        sdcardImages.setClipToPadding(false);
        sdcardImages.setOnItemClickListener(GalleryActivity.this);
        imageAdapter = new ImageAdapter(getApplicationContext()); 
        sdcardImages.setAdapter(imageAdapter);
    }
    /**
     * Load images.
     */
    private void loadImages() {
        final Object data = getLastNonConfigurationInstance();
        if (data == null) {
        	load = new LoadImagesFromSDCard();
        	load.execute();
        } else {
            final LoadedImage[] photos = (LoadedImage[]) data;
            if (photos.length == 0) {
            	load = new LoadImagesFromSDCard();
            	load.execute();
            }
            for (LoadedImage photo : photos) {
                addImage(photo);
            }
        }
    }
    /**
     * Add image(s) to the grid view adapter.
     * 
     * @param value Array of LoadedImages references
     */
    private void addImage(LoadedImage... value) {
        for (LoadedImage image : value) {
            imageAdapter.addPhoto(image);
            imageAdapter.notifyDataSetChanged();
        }
    }
    
    /**
     * Save bitmap images into a list and return that list. 
     * 
     * @return 
     * @see android.app.Activity#onRetainNonConfigurationInstance()
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        final GridView grid = sdcardImages;
        final int count = grid.getChildCount();
        final LoadedImage[] list = new LoadedImage[count];

        for (int i = 0; i < count; i++) {
            final ImageView v = (ImageView) grid.getChildAt(i);
            list[i] = new LoadedImage(((BitmapDrawable) v.getDrawable()).getBitmap());
        }

        return list;
    }
    /**
     * Async task for loading the images from the SD card. 
     * 
     * @author Mihai Fonoage
     *
     */
    class LoadImagesFromSDCard extends AsyncTask<Object, LoadedImage, Integer> {
        
        /**
         * Load images from SD Card in the background, and display each image on the screen. 
         *  
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Integer doInBackground(Object...params ) {
            Bitmap bitmap = null;
            Bitmap newBitmap = null;
            sequencialImageID = new HashMap<Integer, Integer>();

            // Set up an array of the Thumbnail Image ID column we want
            String[] projection = {MediaStore.Images.Media._ID};
            // Create the cursor pointing to the SDCard
            Cursor cursor = managedQuery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, // Which columns to return
                    null,       // Return all rows
                    null,       
                    MediaStore.Images.Media.DATE_TAKEN +" DESC"); 
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int size = cursor.getCount();
            // If size is 0, there are no images on the SD Card.
            if (size == 0) {
            	return -1;
            }
            int imageID = 0;
            for (int i = 0; i < size; i++) {
            	if (isCancelled() || cursor.isClosed()){
            		break;
            	}
                cursor.moveToPosition(i);
                imageID = cursor.getInt(columnIndex);
                sequencialImageID.put(Integer.valueOf(i), imageID);
                    
				bitmap = MediaStore.Images.Thumbnails.getThumbnail(
						getContentResolver(), imageID,
						MediaStore.Images.Thumbnails.MICRO_KIND, null);
				if (bitmap != null) {
					newBitmap = Bitmap.createScaledBitmap(bitmap, 70, 70, true);
					bitmap.recycle();
					if (newBitmap != null) {
						publishProgress(new LoadedImage(newBitmap));
					}
				}
            }
            cursor.close();
            return null;
        }
        /**
         * Add a new LoadedImage in the images grid.
         *
         * @param value The image.
         */
        @Override
        public void onProgressUpdate(LoadedImage... value) {        	
            addImage(value);
        }
        /**
         * Set the visibility of the progress bar to false.
         * 
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Integer result) {   
        	if ((result != null) && (result == -1)){
        		AlertDialog.Builder dialog = new AlertDialog.Builder(sdcardImages.getContext());
        		dialog.setTitle("Aviso");
        		dialog.setMessage("Nenhuma imagem encontrada!");
        		dialog.setNeutralButton("OK", new OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
				    	setResult(RESULT_CANCELED);
				    	finish();
					}
				});
        		dialog.show();
        	}
        	
            setProgressBarIndeterminateVisibility(false);
        }
    }

    /**
     * Adapter for our image files. 
     * 
     * @author Mihai Fonoage
     *
     */
    class ImageAdapter extends BaseAdapter {

        private Context mContext; 
        private ArrayList<LoadedImage> photos = new ArrayList<LoadedImage>();

        public ImageAdapter(Context context) { 
            mContext = context; 
        } 

        public void addPhoto(LoadedImage photo) { 
            photos.add(photo); 
        } 

        public int getCount() { 
            return photos.size(); 
        } 

        public Object getItem(int position) { 
            return photos.get(position); 
        } 

        public long getItemId(int position) { 
            return position; 
        } 

        public View getView(int position, View convertView, ViewGroup parent) { 
            final ImageView imageView; 
            if (convertView == null) { 
                imageView = new ImageView(mContext); 
            } else { 
                imageView = (ImageView) convertView; 
            } 
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setPadding(8, 8, 8, 8);
            imageView.setImageBitmap(photos.get(position).getBitmap());
            return imageView; 
        } 
    }

    /**
     * A LoadedImage contains the Bitmap loaded for the image.
     */
    private static class LoadedImage {
        Bitmap mBitmap;

        LoadedImage(Bitmap bitmap) {
            mBitmap = bitmap;
        }

        public Bitmap getBitmap() {
            return mBitmap;
        }
    }
    /**
     * When an image is clicked, load that image as a puzzle. 
     * @param parent
     * @param v 
     * @param position
     * @param id  
     */
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) { 
    	
    	Integer imageID = sequencialImageID.get(position);    	
   	
    	Uri uri = Uri.parse(MediaStore.Images.Media.EXTERNAL_CONTENT_URI + "/" + imageID);
    	getIntent().setData(uri);
    	setResult(RESULT_OK, getIntent());
		finish();
	}

}