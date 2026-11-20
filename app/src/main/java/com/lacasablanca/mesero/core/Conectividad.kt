package com.lacasablanca.mesero.core

interface Conectividad {
    fun estaEnLineaAhora(): Boolean
}

object ConectividadSiempreEnLinea : Conectividad {
    override fun estaEnLineaAhora(): Boolean = true
}
