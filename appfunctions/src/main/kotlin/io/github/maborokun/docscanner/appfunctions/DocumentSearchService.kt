package io.github.maborokun.docscanner.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint

/**
 * Where the app hands its dependency to the service, which the system creates on its own (possibly in a fresh
 * process, after `Application.onCreate`). Set once at startup by the composition root.
 */
object AppFunctionsHost {
    @Volatile
    var search: DocumentSearch? = null
}

/**
 * Exposes document search to on-device assistants. The KSP processor generates the concrete
 * `DocumentSearchService` from this class and the XML description the system indexes.
 */
@RequiresApi(AppFunctionsController.MIN_SDK)
@AppFunctionServiceEntryPoint(
    serviceName = "DocumentSearchService",
    appFunctionXmlFileName = "document_search_functions",
)
abstract class BaseDocumentSearchService : AppFunctionService() {
    /**
     * Searches the user's scanned documents and receipts.
     *
     * Returns at most 20 matches, newest first, each with its title, scan date, total and page count. An empty
     * [query] lists the newest documents. Only metadata is returned, never the document images or text.
     *
     * @param query Words to look for in document titles, merchant names and recognised text.
     */
    @AppFunctionDeclaration(isEnabled = false, isDescribedByKDoc = true)
    suspend fun searchDocuments(query: String): List<DocumentSummary> {
        val search = checkNotNull(AppFunctionsHost.search) { "The app is not initialised" }
        return search.search(query)
    }
}
