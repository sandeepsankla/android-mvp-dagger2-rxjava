package com.sagarnileshshah.carouselmvp.data.local;


import com.raizlabs.android.dbflow.config.DatabaseDefinition;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;
import com.raizlabs.android.dbflow.structure.database.transaction.Transaction;
import com.sagarnileshshah.carouselmvp.data.DataSource;
import com.sagarnileshshah.carouselmvp.data.models.comment.Comment;
import com.sagarnileshshah.carouselmvp.data.models.comment.Comment_Table;
import com.sagarnileshshah.carouselmvp.data.models.photo.Photo;
import com.sagarnileshshah.carouselmvp.util.threading.MainUiThread;
import com.sagarnileshshah.carouselmvp.util.threading.ThreadExecutor;

import java.util.List;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * The class for fetching from and storing data into a local SQLite DB on a background thread and
 * returning data via callbacks on the main UI thread
 */
public class LocalDataSource extends DataSource {

    private DatabaseDefinition databaseDefinition;

    public LocalDataSource(MainUiThread mainUiThread, ThreadExecutor threadExecutor,
            DatabaseDefinition databaseDefinition) {
        super(mainUiThread, threadExecutor);
        this.databaseDefinition = databaseDefinition;
    }

    @Override
    public void getPhotos(int page, Callback<List<Photo>> onSuccess,
            Callback<Throwable> onError) {

        Observable<List<Photo>> observable = Observable.fromCallable(() -> getPhotosFromDb(page));
        observable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        photos -> onSuccess.call(photos),
                        throwable -> onError.call(throwable));
    }

    private List<Photo> getPhotosFromDb(int page) {
        List<Photo> photos = SQLite.select()
                .from(Photo.class)
                .limit(10)
                .offset((page - 1) * 10)
                .queryList();
        return photos;
    }

    @Override
    public void getComments(final String photoId, final GetCommentsCallback callback) {

        threadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final List<Comment> comments = SQLite.select()
                        .from(Comment.class)
                        .where(Comment_Table.photo_id.eq(photoId))
                        .queryList();


                mainUiThread.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(comments);
                    }
                });
            }
        });
    }


    public void storePhotos(final List<Photo> photos) {
        Transaction transaction = databaseDefinition.beginTransactionAsync(
                new ITransaction() {
                    @Override
                    public void execute(DatabaseWrapper databaseWrapper) {
                        for (Photo photo : photos) {
                            photo.save();
                        }
                    }
                }).build();
        transaction.execute();
    }

    public void storeComments(final Photo photo, final List<Comment> comments) {
        Transaction transaction = databaseDefinition.beginTransactionAsync(
                new ITransaction() {
                    @Override
                    public void execute(DatabaseWrapper databaseWrapper) {
                        for (Comment comment : comments) {
                            comment.setPhoto(photo);
                            comment.save();
                        }
                    }
                }).build();
        transaction.execute();
    }
}
