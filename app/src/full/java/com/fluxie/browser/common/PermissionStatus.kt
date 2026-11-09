package com.fluxie.browser.common

enum class PermissionStatus { ALLOWED, APPROXIMATE, DENIED }

/** Fine location wins; coarse alone is reported as approximate. */
fun locationStatus(fineGranted: Boolean, coarseGranted: Boolean): PermissionStatus = when {
    fineGranted -> PermissionStatus.ALLOWED
    coarseGranted -> PermissionStatus.APPROXIMATE
    else -> PermissionStatus.DENIED
}
