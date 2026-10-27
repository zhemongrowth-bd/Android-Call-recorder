package com.winstone.connect.data

data class EvaluationQuestion(val key: String, val label: String, val points: Int)

object LeadEvaluation {
    const val MAX_SCORE = 90

    val questions = listOf(
        EvaluationQuestion("project_interest", "প্রজেক্টে আগ্রহ দেখিয়েছে?", 15),
        EvaluationQuestion("asked_price", "দাম / পেমেন্ট প্ল্যান জিজ্ঞেস করেছে?", 15),
        EvaluationQuestion("wanted_info", "ব্রোশিউর / WhatsApp / প্রজেক্ট তথ্য চেয়েছে?", 10),
        EvaluationQuestion("visit_interest", "মিটিং / অফিস / সাইট ভিজিটে আগ্রহী?", 20),
        EvaluationQuestion("decision_30d", "৩০ দিনের মধ্যে সিদ্ধান্তের সম্ভাবনা?", 20),
        EvaluationQuestion("wants_follow_up", "ফলো-আপ চেয়েছে?", 10),
    )

    fun isComplete(answers: Map<String, Boolean>) = questions.all { answers[it.key] != null }

    fun score(answers: Map<String, Boolean>) =
        questions.sumOf { if (answers[it.key] == true) it.points else 0 }

    fun category(score: Int): String = when {
        score <= 20 -> "cold"
        score <= 50 -> "warm"
        else -> "hot"
    }
}