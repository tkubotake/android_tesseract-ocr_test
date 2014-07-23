package com.example.tesseract_ocr_test;
/**
 * Tesseract-OCR Example Application for Android (using the tess-two) 
 * 
 * Requires:
 *  * Android NDK environment
 *  * Tess-two library project (https://github.com/rmtheis/tess-two) as library.
 *  * Learned Data (http://tesseract-ocr.googlecode.com/files/tesseract-ocr-3.02.jpn.tar.gz)
 *      into "assets/" directory.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.googlecode.tesseract.android.TessBaseAPI;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class MainActivity extends Activity {
	final static int REQUEST_CODE = 100;
	
	// キャプチャされた画像の保存先パス
	final static String TMP_IMAGE_PATH = Environment.getExternalStorageDirectory() + "/tmp_ocr.jpg";
	// 学習データの保存先パス
	final static String LEARN_DATA_PATH = Environment.getExternalStorageDirectory() + "/learn/";
	// 学習データのファイル名
	final static String LEARN_DATA_FILE_NAME = "jpn.traineddata";
	
	// キャプチャされた画像
	private Bitmap capturedBitmap;
	// キャプチャされた画像を表示するためのImageView
	private ImageView previewImageView;
	// 二値化の閾値
	private int binarizeThreshold = 150;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// 学習データのチェック
		try {
			checkLearnData();
		} catch (IOException e) {
			Toast.makeText(getApplicationContext(), "Error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			e.printStackTrace();
			finish();
		}
		
		// 初期化
		capturedBitmap = null;
		previewImageView = (ImageView) findViewById(R.id.preview_imageview);
		
		Button capture_btn = (Button) findViewById(R.id.capture_btn);
		capture_btn.setOnClickListener(new OnClickListener(){ // Captureボタンが押された時は...
			@Override
			public void onClick(View v) {
				// カメラを用いて写真をキャプチャ
				captureImage();
			}
		});
		
		SeekBar threshold_bar = (SeekBar) findViewById(R.id.threshold_bar);
		threshold_bar.setProgress(binarizeThreshold);
		threshold_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() { // 閾値バーが変更された時は...
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				// 閾値をセット
				binarizeThreshold = progress;
				// 二値化 および 認識処理を実行
				binarizeAndRecognize();
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
	}
	
	/**
	 * カメラを用いて写真をキャプチャ
	 */
	public void captureImage() {
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		Uri image_uri = Uri.fromFile(new File(TMP_IMAGE_PATH));
		intent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
		startActivityForResult(intent, REQUEST_CODE);
	}
	
	/**
	 * 二値化 および 認識処理
	 */
	public void binarizeAndRecognize() {
		if (capturedBitmap == null)
			return;
		
		// 二値化処理の実行
		Bitmap bitmap = binarizeBitmap(capturedBitmap, binarizeThreshold);
		// 二値化された画像をプレビュー
		previewImageView.setImageBitmap(bitmap);
		// 認識処理の実行
		try {
			String result = recognizeBitmap(bitmap);
			((EditText)findViewById(R.id.result_text)).setText(result);
		} catch (Exception e) {
			((EditText)findViewById(R.id.result_text)).setText(e.toString() + e.getLocalizedMessage());
		}
	}
	
	/**
	 * 画像の認識処理(OCR)
	 * @param bitmap　処理対象の画像(Bitmap)
	 * @return 認識された文字列
	 * @throws IOException
	 */
	protected String recognizeBitmap(Bitmap bitmap) throws IOException {
		Bitmap new_bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
		
		// Tesseart-ocrのインスタンスを生成
		TessBaseAPI tes = new TessBaseAPI();
		tes.init(LEARN_DATA_PATH, "jpn"); // 日本語の学習データを使用
		tes.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789"); // 認識対象を数字だけに制限
		tes.setImage(new_bitmap);
		// 認識結果を取得
		String recognized_text = tes.getUTF8Text();
		// インスタンスの終了処理
		tes.end();
		return recognized_text;
	}
	
	/**
	 * 画像の二値化処理
	 * @param bitmap　処理対象の画像(Bitmap)
	 * @param threshold 二値化の閾値
	 * @return　二値化された画像
	 */
	protected Bitmap binarizeBitmap(Bitmap bitmap, int threshold) {
		Bitmap new_bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
		
		for (int y = 0, h = bitmap.getHeight(); y < h; y++) {
			for (int x = 0, w = bitmap.getWidth(); x < w; x++) {
				int pixel = bitmap.getPixel(x, y);
				int gray = (Color.red(pixel)+Color.green(pixel)+Color.blue(pixel))/3;
				if (gray < threshold) {
					new_bitmap.setPixel(x, y, Color.rgb(0, 0, 0));
				} else {
					new_bitmap.setPixel(x, y, Color.rgb(255, 255, 255));
				}
			}
		}
		return new_bitmap;
	}
	
	/**
	 * 学習データのチェック および assets ディレクトリからのコピー処理
	 * @throws IOException
	 * (Tesseract-OCRによる処理の為に、前もって assets ディレクトリからSDカード上に学習データをコピーする)
	 */
	protected void checkLearnData() throws IOException {
		// 学習データの保存先ディレクトリのチェック
		File learn_data_dir = new File(LEARN_DATA_PATH + "tessdata/");
		if (!learn_data_dir.exists())
			learn_data_dir.mkdir();
		
		// 学習データファイルがSDカード上に存在するか否かのチェック
		File learn_data_file = new File(LEARN_DATA_PATH + "tessdata/" + LEARN_DATA_FILE_NAME);
		if (!learn_data_file.exists()) {
			// 学習データをassetsからSDカードへコピー
			Toast.makeText(getApplicationContext(), "学習データをSDカード上にコピーしています...", Toast.LENGTH_SHORT).show();
			InputStream input = getResources().getAssets().open(LEARN_DATA_FILE_NAME);
			FileOutputStream output = new FileOutputStream(LEARN_DATA_PATH + "tessdata/" + LEARN_DATA_FILE_NAME, true);
			byte[] buffer = new byte[1024]; 
			int length; 
			while ((length = input.read(buffer)) >= 0) {
				output.write(buffer, 0, length);
		    }
			input.close();
			output.close();
		}
	}
	
	/**
	 * カメラアプリからの結果を受け取るためのメソッド
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if (requestCode == REQUEST_CODE) {
	    	try {
		    	// キャプチャされた画像ファイルを読み込み
				FileInputStream input = new FileInputStream(new File(TMP_IMAGE_PATH));
				capturedBitmap = BitmapFactory.decodeStream(input);
				
				capturedBitmap = Bitmap.createScaledBitmap(capturedBitmap, capturedBitmap.getWidth()/3, capturedBitmap.getHeight()/3, false);
				
				// 二値化 および 認識処理を実行
				binarizeAndRecognize();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
	    }
	}
}
