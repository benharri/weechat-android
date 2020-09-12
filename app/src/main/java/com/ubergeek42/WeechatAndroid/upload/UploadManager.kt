package com.ubergeek42.WeechatAndroid.upload

import androidx.annotation.MainThread
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root

const val USE_SERVICE = true

interface UploadObserver {
    @MainThread fun onUploadsStarted()
    @MainThread fun onProgress(ratio: Float)
    @MainThread fun onUploadDone(suri: Suri)
    @MainThread fun onUploadFailure(suri: Suri, e: Exception)
    @MainThread fun onFinished()
}


class UploadManager {
    @Root private val kitty = Kitty.make()
    val useService = USE_SERVICE

    val uploads = mutableListOf<Upload>()

    var observer: UploadObserver? = null
        set(observer) {
            field = observer

            observer?.let {
                if (uploads.isNotEmpty()) {
                    it.onUploadsStarted()
                    it.onProgress(getCumulativeRatio())
                }
            }
        }

    @MainThread fun filterUploads(suris: List<Suri>) {
        uploads.removeAll {
            if (it.suri in suris) {
                false
            } else {
                if (it.state == Upload.State.RUNNING) {
                    kitty.info("Cancelling upload: $it")
                    it.cancel()
                }
                true
            }
        }
    }

    @MainThread fun startUploads(suris: List<Suri>) {
        for (suri in suris) {
            if (suri !in uploads.map { it.suri }) {
                startUpload(suri)
            }
        }
    }

    private fun startUpload(suri: Suri) {
        Upload.upload(suri, object : Upload.Listener {
            override fun onStarted(upload: Upload) {
                main {
                    kitty.info("Upload started: $upload")
                    uploads.add(upload)
                    if (useService) UploadService.onUploadStarted(upload)
                    if (uploads.size == 1) {
                        observer?.onUploadsStarted()
                        limiter.reset()
                    }
                }
            }

            override fun onProgress(upload: Upload) {
                main {
                    val ratio = getCumulativeRatio()
                    if (limiter.step(ratio)) {
                        kitty.trace("Upload progress: ${ratio.format(2)}; $upload")
                        if (useService) UploadService.onUploadProgress()
                        observer?.onProgress(ratio)
                    }
                }
            }

            override fun onDone(upload: Upload, httpUri: String) {
                suri.httpUri = httpUri
                main {
                    kitty.info("Upload done: $upload, result: $httpUri")
                    uploads.remove(upload)
                    if (useService) UploadService.onUploadRemoved(upload)
                    observer?.onUploadDone(suri)
                    if (uploads.isEmpty()) observer?.onFinished()
                }
            }

            override fun onFailure(upload: Upload, e: Exception) {
                main {
                    kitty.info("Upload failure: $upload, ${e.javaClass.simpleName}: ${e.message}")
                    uploads.remove(upload)
                    if (useService) UploadService.onUploadRemoved(upload)
                    observer?.onUploadFailure(suri, e)
                    if (uploads.isEmpty()) observer?.onFinished()
                }
            }
        })
    }

    var limiter: SkippingLimiter = SkippingLimiter(min = 0f, max = 1f,
                                              valueThreshold = 0.01f, timeThreshold = 16)

    private fun getCumulativeRatio(): Float {
        val cumulativeTransferredBytes = uploads.map { it.transferredBytes }.sum()
        val cumulativeTotalBytes = uploads.map { it.totalBytes }.sum()
        return cumulativeTransferredBytes fdiv cumulativeTotalBytes
    }

    companion object {
        private val managers = mutableMapOf<Long, UploadManager>().withDefault { UploadManager() }

        @JvmStatic fun forBuffer(buffer: Long): UploadManager = managers.getValue(buffer)
    }
}
