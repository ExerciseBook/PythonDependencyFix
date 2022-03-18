package com.superexercisebook.importscanner

import kotlinx.datetime.LocalDateTime

fun PyPIResult.isAcceptable(): Boolean {
    for (releases in this.releases) {
        for (record in releases.value) {
            if (record.upload_time != null && record.upload_time > LocalDateTime(2019, 1, 1, 0, 0, 0)) {
                return true
            }
        }
    }
    return false
}