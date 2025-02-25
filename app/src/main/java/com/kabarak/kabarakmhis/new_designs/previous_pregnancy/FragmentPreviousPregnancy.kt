package com.kabarak.kabarakmhis.new_designs.previous_pregnancy

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.fhir.FhirEngine
import com.kabarak.kabarakmhis.R
import com.kabarak.kabarakmhis.fhir.FhirApplication
import com.kabarak.kabarakmhis.fhir.viewmodels.PatientDetailsViewModel
import com.kabarak.kabarakmhis.helperclass.DbObservationLabel
import com.kabarak.kabarakmhis.helperclass.DbObservationValues
import com.kabarak.kabarakmhis.helperclass.DbSummaryTitle
import com.kabarak.kabarakmhis.helperclass.FormatterClass
import com.kabarak.kabarakmhis.new_designs.data_class.*
import com.kabarak.kabarakmhis.new_designs.roomdb.KabarakViewModel
import kotlinx.android.synthetic.main.fragment_prev_pregnancy.view.*
import kotlinx.android.synthetic.main.fragment_prev_pregnancy.view.navigation
import kotlinx.android.synthetic.main.navigation.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.Text
import java.time.LocalDate


class  FragmentPreviousPregnancy : Fragment(), AdapterView.OnItemSelectedListener {

    private val formatter = FormatterClass()

    var pregnancyOrderList = arrayOf("Select Pregnancy Order","1st", "2nd", "3rd", "4th", "5th", "6th", "7th")
    private var spinnerPregnancyValue  = pregnancyOrderList[0]

    var pregnancyOutcomeList = arrayOf("Select Pregnancy outcome","Live birth", "Miscarriage", "Abortion")
    private var spinnerPregnancyOutCome  = pregnancyOutcomeList[0]

    private var observationList = mutableMapOf<String, DbObservationLabel>()
    private lateinit var kabarakViewModel: KabarakViewModel

    private lateinit var rootView: View
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    private lateinit var patientId: String
    private lateinit var fhirEngine: FhirEngine

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        rootView = inflater.inflate(R.layout.fragment_prev_pregnancy, container, false)

        kabarakViewModel = KabarakViewModel(requireContext().applicationContext as Application)
        patientId = formatter.retrieveSharedPreference(requireContext(), "patientId").toString()
        fhirEngine = FhirApplication.fhirEngine(requireContext())

        patientDetailsViewModel = ViewModelProvider(this,
            PatientDetailsViewModel.PatientDetailsViewModelFactory(requireContext().applicationContext as Application,fhirEngine, patientId)
        )[PatientDetailsViewModel::class.java]
        kabarakViewModel = KabarakViewModel(requireContext().applicationContext as Application)

        formatter.saveCurrentPage("1", requireContext())
        getPageDetails()

        initSpinner()

        rootView.radioGrpPurperium.setOnCheckedChangeListener { radioGroup, checkedId ->
            val checkedRadioButton = radioGroup.findViewById<RadioButton>(checkedId)
            val isChecked = checkedRadioButton.isChecked
            if (isChecked) {
                val checkedBtn = checkedRadioButton.text.toString()
                if (checkedBtn == "Abnormal") {
                    changeVisibility(rootView.linearPurperium, true)
                } else {
                    changeVisibility(rootView.linearPurperium, false)
                }
            }
        }
        rootView.deliveryMode.setOnCheckedChangeListener { radioGroup, checkedId ->
            val checkedRadioButton = radioGroup.findViewById<RadioButton>(checkedId)
            val isChecked = checkedRadioButton.isChecked
            if (isChecked) {
                val checkedBtn = checkedRadioButton.text.toString()
                if (checkedBtn == "Cesarean Section") {
                    //Guide that duration of labour is 0
                    rootView.etDuration.setText("0")
                    rootView.etDuration.isEnabled = false
                } else {
                    //Guide that duration of labour is not 0
                    rootView.etDuration.isEnabled = true
                }
            }
        }
        rootView.checkboxLabour.setOnCheckedChangeListener { compoundButton, isChecked ->
            if (isChecked) {
                changeVisibility(rootView.linearLabour, false)
            } else {
                changeVisibility(rootView.linearLabour, true)
            }
        }

        rootView.spinnerPregOutcome.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>, p1: View?, position: Int, p3: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()

                if (selectedItem == "Live birth"){
                    rootView.linearPregDetails.visibility = View.VISIBLE
                    rootView.linearBabyDetails.visibility = View.VISIBLE
                }else{
                    rootView.linearPregDetails.visibility = View.GONE
                    rootView.linearBabyDetails.visibility = View.GONE
                }

                spinnerPregnancyOutCome = selectedItem


            }

            override fun onNothingSelected(p0: AdapterView<*>?) {

            }
        }

        rootView.etDuration.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty()) {

                    if (spinnerPregnancyOutCome == "Live birth"){
                        if (s.toString().toInt() == 0) {
                            rootView.etDuration.error = "Duration of labour cannot 0 hours"
                        } else {
                            rootView.etDuration.error = null
                        }
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })


        handleNavigation()

        return rootView
    }

    private fun handleNavigation() {

        rootView.navigation.btnNext.text = "Preview"
        rootView.navigation.btnPrevious.text = "Cancel"

        rootView.navigation.btnNext.setOnClickListener { saveData() }
        rootView.navigation.btnPrevious.setOnClickListener { activity?.onBackPressed() }

    }

    private fun changeVisibility(linearLayout: LinearLayout, showLinear: Boolean){
        if (showLinear){
            linearLayout.visibility = View.VISIBLE
        }else{
            linearLayout.visibility = View.GONE
        }

    }
    




    private fun saveData() {

        val errorList = ArrayList<String>()
        val dbDataList = ArrayList<DbDataList>()

        val year = rootView.etYear.text.toString()
        val ancTime = rootView.etVisitTime.text.toString()
        val birthPlace = rootView.etPlaceOfChildBirth.text.toString()
        val gestation = rootView.etGestation.text.toString()

        val duration = if (rootView.checkboxLabour.isChecked){
            rootView.etDuration.text.toString()
        }else{
            "0"
        }

        val babyWeight = rootView.etBabyWeight.text.toString()

        if (!TextUtils.isEmpty(year) && !TextUtils.isEmpty(gestation)
            && spinnerPregnancyOutCome != pregnancyOutcomeList[0]
            && spinnerPregnancyValue != pregnancyOrderList[0]
        ){

            Log.e("Pregnancy Value", spinnerPregnancyValue)

            addData("Year",year, DbObservationValues.YEAR.name)
            addData("Pregnancy Order",spinnerPregnancyValue, DbObservationValues.PREGNANCY_ORDER.name)
            addData("Gestation",gestation, DbObservationValues.GESTATION.name)
            addData("Pregnancy Outcome",spinnerPregnancyOutCome, DbObservationValues.PREGNANCY_OUTCOME.name)

            for (items in observationList){

                val key = items.key
                val dbObservationLabel = observationList.getValue(key)

                val value = dbObservationLabel.value
                val label = dbObservationLabel.label

                val data = DbDataList(key, value, DbSummaryTitle.A_PREGNANCY_DETAILS.name, DbResourceType.Observation.name, label)
                dbDataList.add(data)

            }

            observationList.clear()
        }else{
            if (TextUtils.isEmpty(year)) errorList.add("Please provide an year")
            if (TextUtils.isEmpty(gestation)) errorList.add("Please provide a Gestation")
            if (spinnerPregnancyOutCome == pregnancyOutcomeList[0]) errorList.add("Please select a pregnancy outcome.")
        }

        if (rootView.linearPregDetails.visibility == View.VISIBLE){
            if (!TextUtils.isEmpty(year) && !TextUtils.isEmpty(ancTime) && !TextUtils.isEmpty(birthPlace)  && !TextUtils.isEmpty(duration) &&
                !TextUtils.isEmpty(babyWeight)){

                addData("ANC Time",ancTime, DbObservationValues.ANC_NO.name)
                addData("Child Birth Place",birthPlace, DbObservationValues.CHILDBIRTH_PLACE.name)
                addData("Duration",duration, DbObservationValues.LABOUR_DURATION.name)

                val deliveryMode = formatter.getRadioText(rootView.deliveryMode)
                if (deliveryMode != ""){
                    addData("Delivery Mode",deliveryMode, DbObservationValues.DELIVERY_MODE.name)
                }else{
                    errorList.add("Delivery Mode is required.")
                }

                for (items in observationList){

                    val key = items.key
                    val dbObservationLabel = observationList.getValue(key)

                    val value = dbObservationLabel.value
                    val label = dbObservationLabel.label

                    val data = DbDataList(key, value, DbSummaryTitle.A_PREGNANCY_DETAILS.name, DbResourceType.Observation.name, label)
                    dbDataList.add(data)

                }

                observationList.clear()

                addData("Baby Weight (grams)",babyWeight, DbObservationValues.BABY_WEIGHT.name)

                val radioGrpBabySex = formatter.getRadioText(rootView.radioGrpBabySex)
                if (radioGrpBabySex != ""){
                    addData("Baby's Sex",radioGrpBabySex, DbObservationValues.BABY_SEX.name)
                }else{
                    errorList.add("The sex of the baby is required")
                }

                val radioGrpOutcome = formatter.getRadioText(rootView.radioGrpOutcome)
                if (radioGrpOutcome != ""){
                    addData("Outcome",radioGrpOutcome, DbObservationValues.BABY_OUTCOME.name)
                }else{
                    errorList.add("Outcome is not selected")
                }

                val radioGrpPurperium = formatter.getRadioText(rootView.radioGrpPurperium)
                if (radioGrpPurperium != ""){
                    addData("Puerperium",radioGrpPurperium, DbObservationValues.BABY_PURPERIUM.name)

                    if (rootView.linearPurperium.visibility == View.VISIBLE){
                        val text = rootView.etAbnormal.text.toString()
                        if (!TextUtils.isEmpty(text)){
                            addData("If Puerperium is Abnormal, ",text, DbObservationValues.ABNORMAL_BABY_PURPERIUM.name)
                        }else{
                            errorList.add("If Puerperium is abnormal, please enter abnormal details")
                        }

                    }

                }else{
                    errorList.add("Please select Puerperium")
                }

                for (items in observationList){

                    val key = items.key
                    val dbObservationLabel = observationList.getValue(key)

                    val value = dbObservationLabel.value
                    val label = dbObservationLabel.label

                    val data = DbDataList(key, value, DbSummaryTitle.B_BABY_DETAILS.name, DbResourceType.Observation.name, label)
                    dbDataList.add(data)

                }
                observationList.clear()

            }else{

                if (TextUtils.isEmpty(ancTime)) errorList.add("Please provide an ANC Time")
                if (TextUtils.isEmpty(birthPlace)) errorList.add("Please provide a Birth Place")
                if (TextUtils.isEmpty(duration)) errorList.add("Please provide a Duration")
                if (TextUtils.isEmpty(babyWeight)) errorList.add("Please provide Baby Weight")
            }
        }

        if (errorList.size == 0) {

            val dbDataDetailsList = ArrayList<DbDataDetails>()
            val dbDataDetails = DbDataDetails(dbDataList)
            dbDataDetailsList.add(dbDataDetails)
            val dbPatientData = DbPatientData(DbResourceViews.PREVIOUS_PREGNANCY.name, dbDataDetailsList)
            kabarakViewModel.insertInfo(requireContext(), dbPatientData)


            val ft = requireActivity().supportFragmentManager.beginTransaction()
            ft.replace(R.id.fragmentHolder, formatter.startFragmentConfirm(requireContext(),
                DbResourceViews.PREVIOUS_PREGNANCY.name))
            ft.addToBackStack(null)
            ft.commit()

        }else{

            formatter.showErrorDialog(errorList, requireContext())

        }

    }

    private fun addData(key: String, value: String, codeLabel: String) {

        val dbObservationLabel = DbObservationLabel(value, codeLabel)
        observationList[key] = dbObservationLabel
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getPageDetails() {

        val totalPages = formatter.retrieveSharedPreference(requireContext(), "totalPages")
        val currentPage = formatter.retrieveSharedPreference(requireContext(), "currentPage")

        if (totalPages != null && currentPage != null){

            formatter.progressBarFun(requireContext(), currentPage.toInt(), totalPages.toInt(), rootView)

        }


    }

    private fun initSpinner() {

        val kinRshp = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, pregnancyOrderList)
        kinRshp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        rootView.spinnerPregOrder!!.adapter = kinRshp
        rootView.spinnerPregOrder.onItemSelectedListener = this


        val pregnancyOutcome = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, pregnancyOutcomeList)
        pregnancyOutcome.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        rootView.spinnerPregOutcome!!.adapter = pregnancyOutcome
        rootView.spinnerPregOutcome.onItemSelectedListener = this


    }

    override fun onItemSelected(arg0: AdapterView<*>, p1: View?, p2: Int, p3: Long) {
        when (arg0.id) {
            R.id.spinnerPregOrder -> { spinnerPregnancyValue = rootView.spinnerPregOrder.selectedItem.toString() }
            R.id.spinnerPregOutcome -> { spinnerPregnancyOutCome = rootView.spinnerPregOutcome.selectedItem.toString() }
            else -> {}
        }
    }

    override fun onNothingSelected(p0: AdapterView<*>?) {
        TODO("Not yet implemented")
    }

    override fun onStart() {
        super.onStart()

        getSavedData()
    }

    private fun getSavedData() {

        try {

            CoroutineScope(Dispatchers.IO).launch {

                val encounterId = formatter.retrieveSharedPreference(requireContext(),
                    DbResourceViews.PREVIOUS_PREGNANCY.name)

                if (encounterId != null){

                    val pregnancyOrder = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.PREGNANCY_ORDER.name), encounterId)

                    val pregnancyOutcome = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.PREGNANCY_OUTCOME.name), encounterId)

                    val year = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.YEAR.name), encounterId)

                    val ancNo = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.ANC_NO.name), encounterId)

                    val birthPlace = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.CHILDBIRTH_PLACE.name), encounterId)

                    val gestation = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.GESTATION.name), encounterId)

                    val duration = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.LABOUR_DURATION.name), encounterId)

                    val deliveryMode = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.DELIVERY_MODE.name), encounterId)

                    val babyWeight = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.BABY_WEIGHT.name), encounterId)

                    val babySex = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.BABY_SEX.name), encounterId)

                    val babyOutcome = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.BABY_OUTCOME.name), encounterId)

                    val babyPurperium = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.BABY_PURPERIUM.name), encounterId)

                    val abnormalPurperium = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.ABNORMAL_BABY_PURPERIUM.name), encounterId)

                    CoroutineScope(Dispatchers.Main).launch {

                        if (pregnancyOrder.isNotEmpty()){
                            val value = pregnancyOrder[0].value
                            rootView.spinnerPregOrder.setSelection(pregnancyOrderList.indexOf(value))
                        }
                        if (pregnancyOutcome.isNotEmpty()){
                            val value = pregnancyOutcome[0].value
                            rootView.spinnerPregOutcome.setSelection(pregnancyOutcomeList.indexOf(value))
                        }
                        if (year.isNotEmpty()){
                            rootView.etYear.setText(year[0].value)
                        }
                        if (ancNo.isNotEmpty()){
                            rootView.etVisitTime.setText(ancNo[0].value)
                        }
                        if (birthPlace.isNotEmpty()){
                            rootView.etPlaceOfChildBirth.setText(birthPlace[0].value)
                        }
                        if (gestation.isNotEmpty()){
                            rootView.etGestation.setText(gestation[0].value)
                        }
                        if (duration.isNotEmpty()){

                            val value = duration[0].value
                            //Check if value is a number
                            if (value.matches("-?\\d+(\\.\\d+)?".toRegex())) {
                                rootView.etDuration.setText(value)
                            }else{
                                rootView.checkboxLabour.isChecked = true
                            }
                        }
                        if (deliveryMode.isNotEmpty()){
                            val value = deliveryMode[0].value
                            if (value.contains("Vaginal Deliver", ignoreCase = true)) rootView.deliveryMode.check(R.id.radioYesVaginal)
                            if (value.contains("Assisted Vaginal Delivery", ignoreCase = true)) rootView.deliveryMode.check(R.id.radioYesAssistedVaginal)
                            if (value.contains("Cesarean Section", ignoreCase = true)) rootView.deliveryMode.check(R.id.radioNoCs)
                        }
                        if (babyWeight.isNotEmpty()){
                            rootView.etBabyWeight.setText(babyWeight[0].value)
                        }
                        if (babySex.isNotEmpty()) {
                            val value = babySex[0].value
                            if (value.contains("Male", ignoreCase = true)) rootView.radioGrpBabySex.check(R.id.radioMaleBabySex)
                            if (value.contains("Female", ignoreCase = true)) rootView.radioGrpBabySex.check(R.id.radioFemaleBabySex)
                        }
                        if (babyOutcome.isNotEmpty()){
                            val value = babyOutcome[0].value
                            if (value.contains("Dead", ignoreCase = true)) rootView.radioGrpOutcome.check(R.id.radioDeadOutcome)
                            if (value.contains("Alive", ignoreCase = true)) rootView.radioGrpOutcome.check(R.id.radioAliveOutcome)
                        }
                        if (babyPurperium.isNotEmpty()){
                            val value = babyPurperium[0].value
                            if (value.contains("Normal", ignoreCase = true)) rootView.radioGrpPurperium.check(R.id.radioNormal)
                            if (value.contains("Abnormal", ignoreCase = true)) rootView.radioGrpPurperium.check(R.id.radioAbnormal)
                        }
                        if (abnormalPurperium.isNotEmpty()){
                            val value = abnormalPurperium[0].value
                            rootView.etAbnormal.setText(value)
                        }



                    }

                }


            }

        }catch (e: Exception){
            e.printStackTrace()
        }

    }

}