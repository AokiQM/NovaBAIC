package com.verlintas.baic2.core.network.provider

import com.verlintas.baic2.core.model.ProviderError

/** Thrown by non-streaming provider calls; carries a typed error. */
class ProviderException(val error: ProviderError) : Exception(error.message)
