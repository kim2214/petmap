package com.kimdev.petmap.data.csv

import java.io.Reader

/**
 * 의존성 없는 최소 CSV 파서.
 * - 따옴표로 감싼 필드 내부의 콤마/개행 처리
 * - 두 개의 따옴표("")를 이스케이프된 따옴표로 처리
 * 레코드(행) 단위로 콜백을 호출한다.
 */
object CsvReader {

    fun forEachRecord(reader: Reader, onRecord: (List<String>) -> Unit) {
        val field = StringBuilder()
        val record = ArrayList<String>(32)
        var inQuotes = false
        var prevCr = false

        fun endField() {
            record.add(field.toString())
            field.setLength(0)
        }
        fun endRecord() {
            endField()
            if (record.size > 1 || record[0].isNotEmpty()) onRecord(ArrayList(record))
            record.clear()
        }

        var ci = reader.read()
        while (ci != -1) {
            val c = ci.toChar()
            when {
                inQuotes -> when (c) {
                    '"' -> {
                        val next = reader.read()
                        if (next.toChar() == '"' && next != -1) {
                            field.append('"') // 이스케이프된 따옴표
                        } else {
                            inQuotes = false
                            ci = next
                            continue
                        }
                    }
                    else -> field.append(c)
                }
                else -> when (c) {
                    '"' -> inQuotes = true
                    ',' -> endField()
                    '\n' -> { endRecord(); prevCr = false }
                    '\r' -> prevCr = true
                    else -> {
                        if (prevCr) { endRecord(); prevCr = false }
                        field.append(c)
                    }
                }
            }
            ci = reader.read()
        }
        // 마지막 레코드 flush
        if (field.isNotEmpty() || record.isNotEmpty()) endRecord()
    }
}
