package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.ArtistInfo
import com.example.data.model.Song
import com.example.data.model.SongComment
import com.example.data.model.SongStats
import com.example.data.model.UserProfile
import com.example.data.model.ViewedProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreMusicRepository(
    private val dbOrNull: FirebaseFirestore?,
    private val auth: FirebaseAuth? = firebaseAuthOrNull()
) {
    // Firebase can be missing (no google-services.json). In that case dbOrNull and auth are
    // null and the app falls back to local storage instead of crashing.
    constructor(context: Context) : this(
        try {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            if (dbId.isBlank() || dbId == "(default)") {
                FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
        } catch (_: Exception) {
            null
        }
    )

    private val db: FirebaseFirestore
        get() = dbOrNull ?: throw IllegalStateException("Firebase is not configured.")

    private fun requireUserId(): String {
        val uid = auth?.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    fun isAuthenticated(): Boolean = dbOrNull != null && !auth?.currentUser?.uid.isNullOrBlank()

    fun currentUserId(): String? = auth?.currentUser?.uid

    fun observeSongStats(song: Song): Flow<SongStats?> = flow {
        val uid = auth?.currentUser?.uid
        if (uid.isNullOrBlank() || dbOrNull == null) {
            emit(null)
            return@flow
        }
        val safeId = song.firestoreId
        val path = "songs/$safeId"
        db.collection("songs").document(safeId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        SongStats::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth?.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                emit(null)
            }
            .collect { emit(it) }
    }

    fun observeComments(song: Song): Flow<List<SongComment>> = flow {
        val uid = auth?.currentUser?.uid
        if (uid.isNullOrBlank() || dbOrNull == null) {
            emit(emptyList())
            return@flow
        }
        val safeId = song.firestoreId
        val path = "songs/$safeId/comments"
        db.collection("songs").document(safeId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SongComment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth?.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw (firestoreError ?: error)
            }
            .collect { emit(it) }
    }

    suspend fun addComment(
        song: Song,
        rawText: String,
        displayName: String
    ): Result<Unit> {
        val safeId = song.firestoreId
        var docPath = "songs/$safeId/comments"
        return try {
            val uid = requireUserId()
            val cleanText = rawText.trim()
            require(cleanText.isNotEmpty() && cleanText.length <= 300) {
                "Comment must be 1 to 300 characters."
            }
            val cleanName = displayName.trim().take(50).ifBlank { "Listener" }
            val songRef = db.collection("songs").document(safeId)
            val commentRef = songRef.collection("comments").document()
            docPath = commentRef.path

            val commentPayload = mapOf(
                "text" to cleanText,
                "name" to cleanName,
                "uid" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            commentRef.set(commentPayload).await()

            val statsPayload = mapOf(
                "title" to song.title.trim().take(200).ifBlank { "Untitled" },
                "artist" to song.artist.trim().take(200).ifBlank { "Unknown Artist" },
                "likeCount" to FieldValue.increment(0L),
                "commentCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            songRef.set(statsPayload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(
        song: Song,
        commentId: String,
        currentCommentCount: Long
    ): Result<Unit> {
        val safeId = song.firestoreId
        val commentPath = "songs/$safeId/comments/$commentId"
        return try {
            requireUserId()
            require(commentId.isNotBlank()) { "Comment ID cannot be blank." }
            val songRef = db.collection("songs").document(safeId)
            songRef.collection("comments").document(commentId).delete().await()

            if (currentCommentCount > 0L) {
                songRef.update(
                    mapOf(
                        "commentCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, commentPath)
            Result.failure(e)
        }
    }

    suspend fun syncSongLike(
        song: Song,
        isNowLiked: Boolean,
        currentRemoteLikeCount: Long
    ): Result<Unit> {
        val safeId = song.firestoreId
        val songPath = "songs/$safeId"
        return try {
            requireUserId()
            if (!isNowLiked && currentRemoteLikeCount <= 0L) {
                return Result.success(Unit)
            }
            val delta = if (isNowLiked) 1L else -1L
            val songRef = db.collection("songs").document(safeId)
            val payload = mapOf(
                "title" to song.title.trim().take(200).ifBlank { "Untitled" },
                "artist" to song.artist.trim().take(200).ifBlank { "Unknown Artist" },
                "likeCount" to FieldValue.increment(delta),
                "commentCount" to FieldValue.increment(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            songRef.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, songPath)
            Result.failure(e)
        }
    }

    // ---- Liked songs, stored per user so they survive reinstalls and new phones ----

    suspend fun saveLikedSong(song: Song, likedAt: Long = System.currentTimeMillis()): Result<Unit> = try {
        val uid = requireUserId()
        val payload = mapOf(
            "id" to song.id,
            "title" to song.title.take(300),
            "artist" to song.artist.take(300),
            "album" to song.album.take(300),
            "duration" to song.duration,
            "image" to song.image,
            "url" to song.url,
            "primaryArtistImage" to song.primaryArtistImage,
            "likedAt" to likedAt
        )
        db.collection("users").document(uid).collection("likedSongs")
            .document(song.firestoreId).set(payload).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("LikedSync", "save failed: ${e.message}")
        Result.failure(e)
    }

    suspend fun deleteLikedSong(song: Song): Result<Unit> = try {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("likedSongs")
            .document(song.firestoreId).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("LikedSync", "delete failed: ${e.message}")
        Result.failure(e)
    }

    /** Returns the user's liked songs with their liked time, or a failure if it can't be read. */
    suspend fun fetchLikedSongs(): Result<List<Pair<Song, Long>>> = try {
        val uid = requireUserId()
        val snap = db.collection("users").document(uid).collection("likedSongs").get().await()
        Result.success(
            snap.documents.mapNotNull { d ->
                val id = d.getString("id") ?: return@mapNotNull null
                val song = Song(
                    id = id,
                    title = d.getString("title").orEmpty(),
                    artist = d.getString("artist").orEmpty(),
                    album = d.getString("album").orEmpty(),
                    duration = (d.getLong("duration") ?: 0L).toInt(),
                    image = d.getString("image").orEmpty(),
                    url = d.getString("url").orEmpty(),
                    primaryArtistImage = d.getString("primaryArtistImage").orEmpty()
                )
                song to (d.getLong("likedAt") ?: 0L)
            }
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("LikedSync", "fetch failed: ${e.message}")
        Result.failure(e)
    }

    // ---- Followed artists, stored per user ----

    private fun artistDocId(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9_\\-]"), "_").take(128).ifBlank { "unknown_artist" }

    suspend fun saveFollowedArtist(name: String, imageUrl: String, followedAt: Long = System.currentTimeMillis()): Result<Unit> = try {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("followedArtists")
            .document(artistDocId(name))
            .set(mapOf("artistName" to name.trim().take(200), "imageUrl" to imageUrl, "followedAt" to followedAt))
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("ArtistSync", "save failed: ${e.message}")
        Result.failure(e)
    }

    suspend fun deleteFollowedArtist(name: String): Result<Unit> = try {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("followedArtists")
            .document(artistDocId(name)).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("ArtistSync", "delete failed: ${e.message}")
        Result.failure(e)
    }

    /** Returns (name, imageUrl, followedAt) for each followed artist in the cloud. */
    suspend fun fetchFollowedArtists(): Result<List<Triple<String, String, Long>>> = try {
        val uid = requireUserId()
        val snap = db.collection("users").document(uid).collection("followedArtists").get().await()
        Result.success(
            snap.documents.mapNotNull { d ->
                val name = d.getString("artistName")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Triple(name, d.getString("imageUrl").orEmpty(), d.getLong("followedAt") ?: 0L)
            }
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("ArtistSync", "fetch failed: ${e.message}")
        Result.failure(e)
    }

    // ---- Profile (display name + username), stored per user ----

    suspend fun saveProfile(displayName: String, username: String): Result<Unit> = try {
        val uid = requireUserId()
        val cleanName = displayName.trim().take(40)
        val cleanUser = username.trim().removePrefix("@").lowercase().take(20)
        // usernameLower / displayNameLower are what other people's searches look at.
        db.collection("users").document(uid).set(
            mapOf(
                "displayName" to cleanName,
                "displayNameLower" to cleanName.lowercase(),
                "username" to cleanUser,
                "usernameLower" to cleanUser,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("ProfileSync", "save failed: ${e.message}")
        Result.failure(e)
    }

    /** Success(null) means the account has no saved profile yet. */
    suspend fun fetchProfile(): Result<Pair<String, String>?> = try {
        val uid = requireUserId()
        val d = db.collection("users").document(uid).get().await()
        if (d.exists()) {
            Result.success(d.getString("displayName").orEmpty() to d.getString("username").orEmpty())
        } else {
            Result.success(null)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("ProfileSync", "fetch failed: ${e.message}")
        Result.failure(e)
    }

    // ---- Search other people & view their public profile ----

    private fun docToProfile(d: DocumentSnapshot): UserProfile? {
        val username = d.getString("username").orEmpty()
        val name = d.getString("displayName").orEmpty()
        if (username.isBlank() && name.isBlank()) return null
        return UserProfile(uid = d.id, displayName = name, username = username)
    }

    /** Finds people whose @username (or display name) starts with [query]. Never returns the current user. */
    suspend fun searchUsers(query: String, limit: Long = 10): Result<List<UserProfile>> = try {
        val me = requireUserId()
        val q = query.trim().removePrefix("@").lowercase()
        if (q.length < 2) {
            Result.success(emptyList())
        } else {
            val byUsername = db.collection("users")
                .orderBy("usernameLower")
                .startAt(q).endAt(q + "\uf8ff")
                .limit(limit).get().await()
            val byName = try {
                db.collection("users")
                    .orderBy("displayNameLower")
                    .startAt(q).endAt(q + "\uf8ff")
                    .limit(limit).get().await().documents
            } catch (_: Exception) {
                emptyList()
            }
            val merged = LinkedHashMap<String, UserProfile>()
            (byUsername.documents + byName).forEach { d ->
                if (d.id != me) docToProfile(d)?.let { merged.putIfAbsent(d.id, it) }
            }
            Result.success(merged.values.toList())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("UserSearch", "search failed: ${e.message}")
        Result.failure(e)
    }

    /** Loads another user's profile with their liked songs and the artists behind them. */
    suspend fun fetchUserProfile(uid: String): Result<ViewedProfile> = try {
        requireUserId()
        require(uid.isNotBlank()) { "Invalid user." }
        val userRef = db.collection("users").document(uid)
        val doc = userRef.get().await()
        val profile = (if (doc.exists()) docToProfile(doc) else null)
            ?: throw IllegalStateException("This profile no longer exists.")

        val likedDocs = userRef.collection("likedSongs")
            .orderBy("likedAt", Query.Direction.DESCENDING)
            .limit(100).get().await().documents
        val liked = likedDocs.mapNotNull { d ->
            val id = d.getString("id") ?: return@mapNotNull null
            Song(
                id = id,
                title = d.getString("title").orEmpty(),
                artist = d.getString("artist").orEmpty(),
                album = d.getString("album").orEmpty(),
                duration = (d.getLong("duration") ?: 0L).toInt(),
                image = d.getString("image").orEmpty(),
                url = d.getString("url").orEmpty(),
                primaryArtistImage = d.getString("primaryArtistImage").orEmpty()
            )
        }

        // Artists of liked songs, the one with most liked songs first.
        val likedArtists = LinkedHashMap<String, Pair<ArtistInfo, Int>>()
        liked.forEach { song ->
            val name = song.firstArtist
            if (name.isBlank() || name.equals("Music", true) || name.equals("Unknown Artist", true)) return@forEach
            val key = name.lowercase()
            val prev = likedArtists[key]
            val image = song.primaryArtistImage.ifBlank { song.image }
            likedArtists[key] = if (prev == null) ArtistInfo(name, image) to 1
            else prev.first to (prev.second + 1)
        }
        val fromLikes = likedArtists.values.sortedByDescending { it.second }.map { it.first }

        val followed = try {
            userRef.collection("followedArtists").get().await().documents.mapNotNull { d ->
                val name = d.getString("artistName")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ArtistInfo(name, d.getString("imageUrl").orEmpty())
            }
        } catch (_: Exception) {
            emptyList()
        }

        val artists = LinkedHashMap<String, ArtistInfo>()
        (fromLikes + followed).forEach { artists.putIfAbsent(it.name.lowercase(), it) }
        Result.success(ViewedProfile(profile, liked, artists.values.toList()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("UserProfile", "load failed: ${e.message}")
        Result.failure(e)
    }
}
