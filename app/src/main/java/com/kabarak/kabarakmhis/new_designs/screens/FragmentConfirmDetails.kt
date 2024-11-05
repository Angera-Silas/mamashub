package com.kabarak.kabarakmhis.new_designs.screens

import android.app.Application
import android.app.ProgressDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.kabarak.kabarakmhis.R
import com.kabarak.kabarakmhis.fhir.FhirApplication
import com.kabarak.kabarakmhis.fhir.viewmodels.AddPatientDetailsViewModel
import com.kabarak.kabarakmhis.helperclass.FormatterClass
import com.kabarak.kabarakmhis.new_designs.NewMainActivity
import com.kabarak.kabarakmhis.new_designs.data_class.*
import com.kabarak.kabarakmhis.new_designs.roomdb.KabarakViewModel
import kotlinx.android.synthetic.main.activity_antenatal_profile_view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Reference

class FragmentConfirmDetails : Fragment() {

    private val formatter = FormatterClass()

    private lateinit var kabarakViewModel: KabarakViewModel

    private lateinit var rootView: View

    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: RecyclerView.LayoutManager

    private lateinit var btnSave: Button

    private var encounterDetailsList = ArrayList<DbConfirmDetails>()

    private lateinit var fhirEngine: FhirEngine
    private val viewModel: AddPatientDetailsViewModel by viewModels()
    private lateinit var patientId: String

    private lateinit var btnEditDetails: Button

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        rootView = inflater.inflate(R.layout.frament_confirm, container, false)

        btnEditDetails = rootView.findViewById(R.id.btnEditDetails)

        patientId = formatter.retrieveSharedPreference(requireContext(), "FHIRID").toString()
        fhirEngine = FhirApplication.fhirEngine(requireContext())

        btnEditDetails.setOnClickListener {
            // Go back to the previous activity
            requireActivity().onBackPressed()
        }

        updateArguments()

        if (savedInstanceState == null) {
            addQuestionnaireFragment()
        }

        kabarakViewModel = KabarakViewModel(requireContext().applicationContext as Application)

        recyclerView = rootView.findViewById(R.id.confirmList)
        layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.VERTICAL,
            false
        )
        recyclerView.layoutManager = layoutManager
        recyclerView.setHasFixedSize(true)

        btnSave = rootView.findViewById(R.id.btnSave)

        btnSave.setOnClickListener {

            val encounter = formatter.retrieveSharedPreference(requireContext(), "encounterTitle")
            if (encounter != null) {

                val progressDialog = ProgressDialog(requireContext())
                progressDialog.setMessage("Saving...")
                progressDialog.setCancelable(false)
                progressDialog.show()

                CoroutineScope(Dispatchers.IO).launch {

                    delay(1000)

                    if (encounterDetailsList.isNotEmpty()) {

                        val saveEncounterId = formatter.retrieveSharedPreference(
                            requireContext(),
                            "savedEncounter"
                        )
                        var encounterId = if (saveEncounterId != null) {
                            if (saveEncounterId != "") {
                                saveEncounterId
                            } else {
                                formatter.generateUuid()
                            }
                        } else {
                            formatter.generateUuid()
                        }

                        Log.e("EncounterId", encounterId)

                        val patientReference = Reference("Patient/$patientId")

                        val questionnaireFragment =
                            childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
                        val questionnaireResponse = questionnaireFragment.getQuestionnaireResponse()

                        val dataQuantityList = ArrayList<QuantityObservation>()
                        val dataCodeList = ArrayList<CodingObservation>()

                        val observationList = kabarakViewModel.getAllObservations(requireContext())

                        for (observation in observationList) {

                            val codeLabel = observation.codeLabel
                            val value = observation.value
                            val display = observation.title

                            val codeValue = formatter.getCodes(codeLabel)
                            val checkObservation = formatter.checkObservations(display)

                            if (checkObservation == "") {
                                // Save as a value string

                                val codingObservation = CodingObservation(
                                    codeValue,
                                    display,
                                    value
                                )
                                dataCodeList.add(codingObservation)

                            } else {
                                // Save as a value quantity
                                val quantityObservation = QuantityObservation(
                                    codeValue,
                                    display,
                                    value,
                                    checkObservation
                                )
                                dataQuantityList.add(quantityObservation)

                            }

                        }

                        viewModel.createEncounter(
                            patientReference,
                            encounterId,
                            questionnaireResponse,
                            dataCodeList,
                            dataQuantityList,
                            encounter
                        )

                        delay(7000)

                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(
                                requireContext(),
                                "Please wait as data is being saved.",
                                Toast.LENGTH_SHORT
                            ).show()
                            progressDialog.dismiss()
                        }

                        CoroutineScope(Dispatchers.IO).launch {
                            kabarakViewModel.deleteTitleTable(encounter, requireContext())
                        }

                        if (encounter == DbResourceViews.PATIENT_INFO.name) {
                            val intent = Intent(requireContext(), NewMainActivity::class.java)
                            startActivity(intent)
                            activity?.finish()

                        } else {

                            val intent = Intent(requireContext(), PatientProfile::class.java)
                            startActivity(intent)
                            activity?.finish()
                        }

                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(requireContext(), "No data to save", Toast.LENGTH_SHORT)
                                .show()
                            progressDialog.dismiss()
                        }
                    }

                }

            }

        }

        return rootView
    }

    override fun onStart() {
        super.onStart()
        getConfirmDetails()
    }

    private fun updateArguments() {
        requireArguments()
            .putString(QUESTIONNAIRE_FILE_PATH_KEY, "client.json")
    }

    private fun addQuestionnaireFragment() {
        val fragment = QuestionnaireFragment.builder()
            .setQuestionnaire(viewModel.questionnaire) // Ensure viewModel.questionnaire returns the JSON string
            .build()

        childFragmentManager.commit {
            setReorderingAllowed(true)
            add(R.id.add_patient_container, fragment, QUESTIONNAIRE_FRAGMENT_TAG)
        }
    }

    private fun getConfirmDetails() {

        // Get the data from the previous screen
        // Use fhirId, loggedIn User, and title

        encounterDetailsList = kabarakViewModel.getConfirmDetails(requireContext())
        val confirmParentAdapter = ConfirmParentAdapter(encounterDetailsList, requireContext())
        recyclerView.adapter = confirmParentAdapter

        getUserDetails()

    }

    private fun getUserDetails() {

        val identifier = formatter.retrieveSharedPreference(requireContext(), "identifier")
        val patientName = formatter.retrieveSharedPreference(requireContext(), "patientName")

        if (identifier != null && patientName != null) {
            tvPatient.text = patientName
            tvAncId.text = identifier
        }

    }

    companion object {
        const val QUESTIONNAIRE_FILE_PATH_KEY = "questionnaire-file-path-key"
        const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
    }

}