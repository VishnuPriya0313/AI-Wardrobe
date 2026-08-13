(function () {
  const createElement = React.createElement;
  const BACKEND_API_BASE_URL = resolveBackendApiBaseUrl();
  const MAX_ANALYSIS_IMAGE_DIMENSION = 768;
  const ANALYSIS_IMAGE_QUALITY = 0.8;
  const MAX_MATCH_CANDIDATES = 3;
  const MAX_VISUAL_MATCH_FINALISTS = 6;
  const MAX_SHOPPING_OPTIONS = 5;
  const MAX_BULK_UPLOAD_FILES = 20;
  const MAX_IMAGE_FILE_BYTES = 15 * 1024 * 1024;
  const REQUEST_TIMEOUT_MS = 120000;
  let csrfTokenPromise;
  const FIELD_SUGGESTIONS = {
    category: ["top", "bottom"],
    color: ["black", "white", "navy", "blue", "gray", "beige", "brown", "green", "red", "pink", "purple", "yellow", "orange"],
    material: ["cotton", "linen", "denim", "knit", "ribbed knit", "chiffon", "satin", "leather", "wool", "polyester"],
    pattern: ["solid", "striped", "floral", "plaid", "checked", "polka dot", "graphic", "lace", "ribbed"],
    occasion: ["casual", "smart casual", "work", "formal", "party", "lounge", "athletic", "beach"],
    season: ["spring", "summer", "fall", "winter", "spring/summer", "fall/winter", "all season"]
  };

  const EMPTY_CLOTHING_ANALYSIS = {
    name: "",
    color: "",
    category: "",
    pattern: "",
    material: "",
    occasion: "",
    season: "",
  };

  const SAMPLE_WARDROBE_ITEMS = [
    { sampleKey: "sample-ivory-linen-shirt", name: "Ivory Linen Shirt", category: "top", colorA: "#f1eee6", colorB: "#d4c7b5", occasion: "smart casual" },
    { sampleKey: "sample-black-ribbed-tee", name: "Black Ribbed Tee", category: "top", colorA: "#14171a", colorB: "#575f66", occasion: "casual" },
    { sampleKey: "sample-sage-overshirt", name: "Sage Overshirt", category: "top", colorA: "#8fa58e", colorB: "#dce6d9", occasion: "street" },
    { sampleKey: "sample-indigo-straight-jeans", name: "Indigo Straight Jeans", category: "bottom", colorA: "#273f66", colorB: "#8aa0bd", occasion: "casual" },
    { sampleKey: "sample-charcoal-tailored-trouser", name: "Charcoal Tailored Trouser", category: "bottom", colorA: "#343a40", colorB: "#a2aab0", occasion: "formal" },
    { sampleKey: "sample-warm-sand-wide-pant", name: "Warm Sand Wide Pant", category: "bottom", colorA: "#c5a876", colorB: "#f0e4cd", occasion: "smart casual" }
  ];
  const SAMPLE_KEY_BY_NAME = new Map(
    SAMPLE_WARDROBE_ITEMS.map((sample) => [normalizeDuplicateText(sample.name), sample.sampleKey])
  );

  function WardrobeStudioApp() {
    const [wardrobeItems, setWardrobeItems] = React.useState([]);
    const [currentUser, setCurrentUser] = React.useState(null);
    const [isCheckingSession, setIsCheckingSession] = React.useState(true);
    const [activeWardrobeFilter, setActiveWardrobeFilter] = React.useState("all");
    const [selectedWardrobeItemId, setSelectedWardrobeItemId] = React.useState("");
    const [selectedImagePreview, setSelectedImagePreview] = React.useState("");
    const [selectedImageFingerprint, setSelectedImageFingerprint] = React.useState("");
    const [selectedFileName, setSelectedFileName] = React.useState("");
    const [editingWardrobeItemId, setEditingWardrobeItemId] = React.useState("");
    const [recognizedClothingDetails, setRecognizedClothingDetails] = React.useState(EMPTY_CLOTHING_ANALYSIS);
    const [uploadState, setUploadState] = React.useState({ text: "", tone: "" });
    const [bulkUploadState, setBulkUploadState] = React.useState({ active: false, current: 0, total: 0, results: [] });
    const [appState, setAppState] = React.useState({ text: "", tone: "" });
    const [isWardrobeHydrated, setIsWardrobeHydrated] = React.useState(false);
    const [isRecognizingClothing, setIsRecognizingClothing] = React.useState(false);
    const [isScoringOutfits, setIsScoringOutfits] = React.useState(false);
    const [outfitScoreResults, setOutfitScoreResults] = React.useState([]);
    const [shoppingOptions, setShoppingOptions] = React.useState([]);
    const [shoppingStatus, setShoppingStatus] = React.useState({ text: "", tone: "" });
    const [isSearchingShopping, setIsSearchingShopping] = React.useState(false);
    const [shoppingTargetType, setShoppingTargetType] = React.useState("");
    const [isShoppingTypeMenuOpen, setIsShoppingTypeMenuOpen] = React.useState(false);
    const [activeSuggestionField, setActiveSuggestionField] = React.useState("");
    const [activeAppView, setActiveAppView] = React.useState("wardrobe");
    const [isDarkMode, setIsDarkMode] = React.useState(() => {
      const saved = localStorage.getItem("ai-wardrobe-theme");
      if (saved) return saved === "dark";
      return window.matchMedia("(prefers-color-scheme: dark)").matches;
    });
    const matcherPanelRef = React.useRef(null);
    const removedWardrobeItemIdsRef = React.useRef(new Set());
    const syncingWardrobeItemIdsRef = React.useRef(new Set());
    const [isProfileOpen, setIsProfileOpen] = React.useState(false);
    const [accountProfile, setAccountProfile] = React.useState(null);
    const [profileState, setProfileState] = React.useState({ loading: false, error: "" });
    const [deleteAccountConfirmation, setDeleteAccountConfirmation] = React.useState("");
    const [deleteAccountState, setDeleteAccountState] = React.useState({ busy: false, error: "" });
    const [isDeleteConfirmationOpen, setIsDeleteConfirmationOpen] = React.useState(false);
    const [isDraggingUpload, setIsDraggingUpload] = React.useState(false);
    const profileDialogRef = React.useRef(null);

    React.useEffect(() => {
      // Remove the short-lived local wardrobe store used by an earlier build.
      try {
        window.indexedDB?.deleteDatabase("ai-wardrobe-local");
      } catch (error) {}
      fetch(`${BACKEND_API_BASE_URL}/api/auth/me`, { credentials: "include" })
        .then(async (response) => response.ok ? response.json() : null)
        .then((user) => user?.authenticated ? handleAuthenticated(user) : handleSignedOut())
        .catch(handleSignedOut)
        .finally(() => setIsCheckingSession(false));
    }, []);


    React.useEffect(() => {
      document.documentElement.setAttribute("data-theme", isDarkMode ? "dark" : "light");
      document.querySelector('meta[name="theme-color"]')?.setAttribute("content", isDarkMode ? "#0f1117" : "#f4f6f8");
      localStorage.setItem("ai-wardrobe-theme", isDarkMode ? "dark" : "light");
    }, [isDarkMode]);

    React.useEffect(() => {
      let isCancelled = false;
      if (!currentUser) return undefined;
      setIsWardrobeHydrated(false);
      fetchStoredWardrobeItems()
        .then((storedItems) => {
          if (isCancelled) return;
          const normalizedStoredItems = storedItems.map(normalizeWardrobeItem);
          setWardrobeItems((currentItems) => deduplicateWardrobeItems(currentItems.concat(normalizedStoredItems)));
          setIsWardrobeHydrated(true);
          if (normalizedStoredItems.length) {
            setAppState({ text: `Wardrobe ready · ${normalizedStoredItems.length} ${normalizedStoredItems.length === 1 ? "item" : "items"} restored`, tone: "ready" });
          }
        })
        .catch((error) => {
          if (isCancelled) return;
          setIsWardrobeHydrated(true);
          setAppState({ text: `Cloud wardrobe could not be loaded: ${error.message || "storage is unavailable."}`, tone: "error" });
        });
      return () => {
        isCancelled = true;
      };
    }, [currentUser?.username]);

    React.useEffect(() => {
      if (!appState.text || appState.tone === "busy") return undefined;
      const dismissTimer = window.setTimeout(() => {
        setAppState((currentState) => currentState.text === appState.text ? { text: "", tone: "" } : currentState);
      }, appState.tone === "error" ? 7000 : 4200);
      return () => window.clearTimeout(dismissTimer);
    }, [appState.text, appState.tone]);

    React.useEffect(() => {
      if (!isProfileOpen) return undefined;
      const previouslyFocused = document.activeElement;
      const dialog = profileDialogRef.current;
      const focusableSelector = "button:not(:disabled), input:not(:disabled), [tabindex]:not([tabindex='-1'])";
      requestAnimationFrame(() => dialog?.querySelector(focusableSelector)?.focus());

      function handleDialogKeyDown(event) {
        if (event.key === "Escape" && dialog?.dataset.busy !== "true") {
          setIsProfileOpen(false);
          return;
        }
        if (event.key !== "Tab" || !dialog) return;
        const focusableItems = Array.from(dialog.querySelectorAll(focusableSelector));
        if (!focusableItems.length) return;
        const firstItem = focusableItems[0];
        const lastItem = focusableItems[focusableItems.length - 1];
        if (event.shiftKey && document.activeElement === firstItem) {
          event.preventDefault();
          lastItem.focus();
        } else if (!event.shiftKey && document.activeElement === lastItem) {
          event.preventDefault();
          firstItem.focus();
        }
      }

      document.addEventListener("keydown", handleDialogKeyDown);
      return () => {
        document.removeEventListener("keydown", handleDialogKeyDown);
        previouslyFocused?.focus?.();
      };
    }, [isProfileOpen]);

    React.useEffect(() => {
      if (!isWardrobeHydrated) return;
      wardrobeItems
        .filter((item) => !isSampleWardrobeItem(item))
        .filter((item) => !item.cloudStorage?.stored)
        .filter((item) => !syncingWardrobeItemIdsRef.current.has(item.id))
        .forEach((item) => {
          syncingWardrobeItemIdsRef.current.add(item.id);
          backupWardrobeItemToR2(item)
            .catch(() => {})
            .finally(() => syncingWardrobeItemIdsRef.current.delete(item.id));
        });
    }, [wardrobeItems, isWardrobeHydrated]);

    const selectedWardrobeItem = wardrobeItems.find((item) => item.id === selectedWardrobeItemId);
    const outfitCandidateItems = selectedWardrobeItem ? wardrobeItems.filter((item) => item.id !== selectedWardrobeItem.id && canItemsCreateOutfitPair(selectedWardrobeItem, item)) : [];
    const displayedWardrobeItems = activeWardrobeFilter === "all" ? wardrobeItems : wardrobeItems.filter((item) => item.category === activeWardrobeFilter);
    const topItemCount = wardrobeItems.filter((item) => item.category === "top").length;
    const bottomItemCount = wardrobeItems.filter((item) => item.category === "bottom").length;
    const dressItemCount = wardrobeItems.filter((item) => item.category === "dress").length;
    const sampleItemCount = wardrobeItems.filter(isSampleWardrobeItem).length;
    const missingSampleCount = Math.max(0, SAMPLE_WARDROBE_ITEMS.length - sampleItemCount);
    React.useEffect(() => {
      setOutfitScoreResults([]);
      setShoppingOptions([]);
      setShoppingStatus({ text: "", tone: "" });
      setShoppingTargetType("");
      setIsShoppingTypeMenuOpen(false);
      setIsScoringOutfits(false);
    }, [selectedWardrobeItemId]);

    async function handleClothingImageSelected(event) {
      const selectedFiles = Array.from(event.target.files || []);
      event.target.value = "";
      await processSelectedClothingFiles(selectedFiles);
    }

    async function handleClothingFilesDropped(event) {
      event.preventDefault();
      setIsDraggingUpload(false);
      if (bulkUploadState.active) return;
      await processSelectedClothingFiles(Array.from(event.dataTransfer?.files || []));
    }

    async function processSelectedClothingFiles(selectedFiles) {
      if (selectedFiles.length > 1) {
        await processBulkClothingImages(selectedFiles);
        return;
      }
      const selectedFile = selectedFiles[0];
      if (!selectedFile) return;
      try {
        validateImageFile(selectedFile);
      } catch (error) {
        setUploadState({ text: error.message, tone: "error" });
        return;
      }
      setIsRecognizingClothing(true);
      setSelectedFileName(selectedFile.name);
      setUploadState({ text: "AI recognizing...", tone: "busy" });

      try {
        const uploadedImageDataUrl = await readImageFileAsOptimizedDataUrl(selectedFile);
        const imageFingerprint = await createImageFingerprint(uploadedImageDataUrl);
        const duplicateItem = findDuplicateWardrobeItem(wardrobeItems, {
          imageFingerprint,
          image: uploadedImageDataUrl
        });

        if (duplicateItem) {
          setSelectedImagePreview("");
          setSelectedImageFingerprint("");
          setRecognizedClothingDetails(EMPTY_CLOTHING_ANALYSIS);
          setUploadState({ text: `Already saved as ${duplicateItem.analysis?.name || "an item"}.`, tone: "error" });
          return;
        }

        setSelectedImagePreview(uploadedImageDataUrl);
        setSelectedImageFingerprint(imageFingerprint);
        const analysisResult = await sendJsonToBackend("/api/analyze-clothing", { image: uploadedImageDataUrl });
        setRecognizedClothingDetails(normalizeClothingAnalysis(analysisResult));
        setUploadState({ text: "AI recognized.", tone: "ready" });
      } catch (error) {
        setRecognizedClothingDetails(EMPTY_CLOTHING_ANALYSIS);
        setUploadState({ text: error.message || "Could not analyze this image.", tone: "error" });
      } finally {
        setIsRecognizingClothing(false);
      }
    }

    async function processBulkClothingImages(selectedFiles) {
      const files = selectedFiles.slice(0, MAX_BULK_UPLOAD_FILES);
      const initialResult = files.map(() => ({ status: "waiting", message: "Queued" }));
      setBulkUploadState({ active: true, current: 0, total: files.length, results: initialResult });
      setIsRecognizingClothing(true);
      setUploadState({ text: `Preparing ${files.length} images...`, tone: "busy" });
      const newItems = [];

      for (let index = 0; index < files.length; index += 1) {
        const file = files[index];
        setBulkUploadState((state) => Object.assign({}, state, {
          current: index + 1,
          results: state.results.map((result, resultIndex) => resultIndex === index
            ? Object.assign({}, result, { status: "processing", message: "AI analyzing..." })
            : result)
        }));
        setUploadState({ text: `Analyzing item ${index + 1} of ${files.length}`, tone: "busy" });

        try {
          validateImageFile(file);
          const image = await readImageFileAsOptimizedDataUrl(file);
          const imageFingerprint = await createImageFingerprint(image);
          const duplicateItem = findDuplicateWardrobeItem(wardrobeItems.concat(newItems), { imageFingerprint, image });
          if (duplicateItem) {
            setBulkResult(index, "skipped", `Duplicate of ${duplicateItem.analysis?.name || "saved item"}`);
            continue;
          }

          const analysis = normalizeClothingAnalysis(await sendJsonToBackend("/api/analyze-clothing", { image }));
          if (!analysis.name || !analysis.category) throw new Error("AI could not identify this clothing item.");
          const wardrobeItem = {
            id: createWardrobeItemId(), image, imageFingerprint, originalFileName: file.name,
            analysis, category: analysis.category, createdAt: new Date().toISOString()
          };
          const storageResult = await storeWardrobeItemInCloud(wardrobeItem);
          const storedWardrobeItem = Object.assign({}, wardrobeItem, { cloudStorage: storageResult });
          newItems.push(storedWardrobeItem);
          setWardrobeItems((currentItems) => deduplicateWardrobeItems([storedWardrobeItem].concat(currentItems)));
          setBulkResult(index, "saved", "Saved to cloud");
        } catch (error) {
          setBulkResult(index, "failed", error.message || "Could not process image.");
        }
      }

      const skippedForLimit = selectedFiles.length - files.length;
      setBulkUploadState((state) => Object.assign({}, state, { active: false, current: files.length }));
      setIsRecognizingClothing(false);
      setUploadState({
        text: `${newItems.length} of ${files.length} items saved to cloud.${skippedForLimit ? ` Only the first ${MAX_BULK_UPLOAD_FILES} files were processed.` : ""}`,
        tone: newItems.length ? "ready" : "error"
      });
      if (newItems.length) setAppState({ text: `${newItems.length} ${newItems.length === 1 ? "item" : "items"} saved to cloud.`, tone: "ready" });
    }

    function setBulkResult(index, status, message) {
      setBulkUploadState((state) => Object.assign({}, state, {
        results: state.results.map((result, resultIndex) => resultIndex === index
          ? Object.assign({}, result, { status, message })
          : result)
      }));
    }

    function cancelSelectedUpload() {
      setSelectedImagePreview("");
      setSelectedImageFingerprint("");
      setSelectedFileName("");
      setRecognizedClothingDetails(EMPTY_CLOTHING_ANALYSIS);
      setEditingWardrobeItemId("");
      setUploadState({ text: "", tone: "" });
    }

    function editWardrobeItem(item, event) {
      event.stopPropagation();
      setEditingWardrobeItemId(item.id);
      setSelectedImagePreview(item.image);
      setSelectedImageFingerprint(item.imageFingerprint || "");
      setSelectedFileName(item.originalFileName || "");
      setRecognizedClothingDetails(normalizeClothingAnalysis(item.analysis));
      setUploadState({ text: "", tone: "" });
      setBulkUploadState({ active: false, current: 0, total: 0, results: [] });
      setActiveAppView("add");
      window.scrollTo({ top: 0, behavior: "smooth" });
    }

    function updateRecognizedClothingField(fieldName, value) {
      setRecognizedClothingDetails((currentDetails) => Object.assign({}, currentDetails, {
        [fieldName]: value
      }));
    }

    async function saveRecognizedClothingItem() {
      if (!selectedImagePreview || !recognizedClothingDetails.name || !recognizedClothingDetails.category) return;
      const editedClothingDetails = normalizeClothingAnalysis(recognizedClothingDetails);
      const duplicateItem = findDuplicateWardrobeItem(wardrobeItems.filter((item) => item.id !== editingWardrobeItemId), {
        imageFingerprint: selectedImageFingerprint,
        image: selectedImagePreview,
        analysis: editedClothingDetails
      });

      if (duplicateItem) {
        cancelSelectedUpload();
        setUploadState({ text: `Already saved as ${duplicateItem.analysis?.name || "an item"}.`, tone: "error" });
        return;
      }

      const originalItem = editingWardrobeItemId ? wardrobeItems.find((item) => item.id === editingWardrobeItemId) : null;
      const wardrobeItem = Object.assign({}, originalItem || {}, {
        id: editingWardrobeItemId || createWardrobeItemId(),
        image: selectedImagePreview,
        imageFingerprint: selectedImageFingerprint,
        originalFileName: selectedFileName,
        analysis: editedClothingDetails,
        category: editedClothingDetails.category,
        createdAt: originalItem?.createdAt || new Date().toISOString()
      });
      const wasEditing = Boolean(editingWardrobeItemId);
      setAppState({ text: wasEditing ? "Saving changes to cloud..." : "Saving item to cloud...", tone: "busy" });
      try {
        const storageResult = isSampleWardrobeItem(wardrobeItem)
          ? wardrobeItem.cloudStorage
          : await storeWardrobeItemInCloud(wardrobeItem);
        const storedWardrobeItem = Object.assign({}, wardrobeItem, { cloudStorage: storageResult });
        setWardrobeItems((currentItems) => editingWardrobeItemId
          ? currentItems.map((item) => item.id === editingWardrobeItemId ? storedWardrobeItem : item)
          : deduplicateWardrobeItems([storedWardrobeItem].concat(currentItems)));
        cancelSelectedUpload();
        setAppState({ text: wasEditing ? "Changes saved to cloud." : "Item saved to cloud.", tone: "ready" });
      } catch (error) {
        setAppState({ text: `Could not save to cloud: ${error.message || "storage is unavailable."}`, tone: "error" });
      }
    }

    function removeWardrobeItem(itemId, event) {
      event.stopPropagation();
      const item = wardrobeItems.find((i) => i.id === itemId);
      if (item && !confirm(`Remove ${item.analysis?.name || "this item"} from your wardrobe?`)) return;
      const removeItemLocally = () => {
        setWardrobeItems((currentItems) => currentItems.filter((item) => item.id !== itemId));
        if (selectedWardrobeItemId === itemId) setSelectedWardrobeItemId("");
        setOutfitScoreResults([]);
      };

      if (!item || isSampleWardrobeItem(item)) {
        removeItemLocally();
        return;
      }

      if (!isSampleWardrobeItem(item)) {
        removedWardrobeItemIdsRef.current.add(itemId);
        deleteStoredWardrobeItem(itemId)
          .then(removeItemLocally)
          .catch((error) => {
            removedWardrobeItemIdsRef.current.delete(itemId);
            setAppState({ text: error.message || "Could not delete the R2 copy, so the item was kept.", tone: "error" });
          });
      }
    }

    function selectWardrobeItem(itemId) {
      if (isScoringOutfits) return;
      setSelectedWardrobeItemId(itemId);
      setOutfitScoreResults([]);
      setActiveAppView("match");
      requestAnimationFrame(() => {
        matcherPanelRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    }

    function loadSampleWardrobeItems() {
      setWardrobeItems((currentItems) => {
        const userWardrobeItems = deduplicateWardrobeItems(currentItems).filter((item) => !isSampleWardrobeItem(item));
        const sampleItems = SAMPLE_WARDROBE_ITEMS.map(createSampleWardrobeItem);
        const refreshedWardrobeItems = deduplicateWardrobeItems(sampleItems.concat(userWardrobeItems));
        return refreshedWardrobeItems;
      });
    }

    function createSampleWardrobeItem({ sampleKey, name, category, colorA, colorB, occasion }) {
      const image = createSampleWardrobeItemImage(name, category, colorA, colorB);
      return {
        id: sampleKey,
        sampleKey,
        imageFingerprint: sampleKey,
        image,
        originalFileName: "sample",
        category,
        createdAt: new Date().toISOString(),
        analysis: {
          name,
          color: name.split(" ")[0].toLowerCase(),
          category,
          pattern: name.includes("Jeans") ? "denim" : "solid",
          material: name.includes("Linen") ? "linen" : name.includes("Ribbed") ? "cotton knit" : "woven fabric",
          occasion,
          season: "all season"
        }
      };
    }

    function clearWardrobe() {
      if (!wardrobeItems.some(isSampleWardrobeItem)) {
        return;
      }

      if (!confirm("Clear sample wardrobe items? Uploaded items will stay saved.")) return;
      setWardrobeItems((currentItems) => {
        const selectedItemWillBeRemoved = currentItems.some((item) => item.id === selectedWardrobeItemId && isSampleWardrobeItem(item));
        const uploadedItems = currentItems.filter((item) => !isSampleWardrobeItem(item));
        if (selectedItemWillBeRemoved) setSelectedWardrobeItemId("");
        setOutfitScoreResults([]);
        return uploadedItems;
      });
    }

    async function analyzeSelectedItemOutfitScores() {
      if (!selectedWardrobeItem || !outfitCandidateItems.length) return;
      setIsScoringOutfits(true);
      setOutfitScoreResults([]);

      try {
        const finalists = shortlistOutfitCandidates(selectedWardrobeItem, outfitCandidateItems)
          .slice(0, MAX_VISUAL_MATCH_FINALISTS);
        setAppState({ text: "Visually comparing the strongest outfit options...", tone: "busy" });
        const visualBatchResult = await sendJsonToBackend("/api/score-outfits", {
          selectedLabel: buildWardrobeItemDescription(selectedWardrobeItem),
          selectedImage: selectedWardrobeItem.image,
          candidates: finalists.map((candidate) => ({
            id: candidate.id,
            label: buildWardrobeItemDescription(candidate),
            image: candidate.image
          }))
        });
        const candidateById = new Map(finalists.map((candidate) => [candidate.id, candidate]));
        const top3 = (visualBatchResult.results || [])
          .map((result) => ({
            candidateId: result.candidateId,
            candidate: candidateById.get(result.candidateId),
            score: clampOutfitScore(result.score),
            verdict: result.verdict || ""
          }))
          .filter((result) => result.candidate)
          .sort((a, b) => b.score - a.score)
          .slice(0, MAX_MATCH_CANDIDATES);

        setOutfitScoreResults(top3);
        setAppState({ text: "Best visually refined matches ready.", tone: "ready" });
      } catch (error) {
        setAppState({ text: error.message || "AI matching failed.", tone: "error" });
      } finally {
        setIsScoringOutfits(false);
      }
    }

    async function findShoppingOptions() {
      if (!selectedWardrobeItem) return;
      if (!shoppingTargetType) {
        setShoppingStatus({ text: "Choose what type of item you want to shop for.", tone: "error" });
        return;
      }
      setIsSearchingShopping(true);
      setShoppingOptions([]);
      setShoppingStatus({ text: "Analyzing current store options...", tone: "busy" });
      try {
        const options = await sendJsonToBackend("/api/shopping-options", {
          selectedItem: buildWardrobeItemDescription(selectedWardrobeItem),
          targetCategory: selectedWardrobeItem.category === "bottom" ? "top" : "bottom",
          targetType: shoppingTargetType
        });
        const visibleOptions = Array.isArray(options) ? options.slice(0, MAX_SHOPPING_OPTIONS) : [];
        setShoppingOptions(visibleOptions);
        setShoppingStatus(visibleOptions.length
          ? { text: `Showing ${visibleOptions.length} current polished matches.`, tone: "ready" }
          : { text: "No online products were found. Try another type.", tone: "error" });
      } catch (error) {
        setShoppingStatus({ text: error.message || "Online shopping search failed.", tone: "error" });
      } finally {
        setIsSearchingShopping(false);
      }
    }

    async function backupWardrobeItemToR2(wardrobeItem) {
      try {
        const storageResult = await storeWardrobeItemInCloud(wardrobeItem);
        if (removedWardrobeItemIdsRef.current.has(wardrobeItem.id)) {
          await deleteStoredWardrobeItem(wardrobeItem.id);
          removedWardrobeItemIdsRef.current.delete(wardrobeItem.id);
          return;
        }

        setWardrobeItems((currentItems) => currentItems.map((item) =>
          item.id === wardrobeItem.id ? Object.assign({}, item, { cloudStorage: storageResult }) : item
        ));
        setAppState({ text: "Item saved to cloud.", tone: "ready" });
        return storageResult;
      } catch (error) {
        setAppState({ text: `Could not save to cloud: ${error.message || "storage is unavailable."}`, tone: "error" });
        throw error;
      }
    }

    async function storeWardrobeItemInCloud(wardrobeItem) {
      const storageResult = await sendJsonToBackend("/api/wardrobe-items", wardrobeItem);
      if (!storageResult || storageResult.stored !== true) {
        throw new Error("Cloud storage did not confirm the save.");
      }
      return storageResult;
    }

    async function deleteStoredWardrobeItem(itemId) {
      const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/wardrobe-items/${encodeURIComponent(itemId)}`, { method: "DELETE" });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(result.error || result.detail || "Item removed locally, but the R2 copy could not be deleted.");
      }
      return result;
    }

    function resetUserWorkspace() {
      setWardrobeItems([]);
      setActiveWardrobeFilter("all");
      setSelectedWardrobeItemId("");
      setSelectedImagePreview("");
      setSelectedImageFingerprint("");
      setSelectedFileName("");
      setEditingWardrobeItemId("");
      setRecognizedClothingDetails(EMPTY_CLOTHING_ANALYSIS);
      setUploadState({ text: "", tone: "" });
      setBulkUploadState({ active: false, current: 0, total: 0, results: [] });
      setAppState({ text: "", tone: "" });
      setIsWardrobeHydrated(false);
      setOutfitScoreResults([]);
      setShoppingOptions([]);
      setShoppingStatus({ text: "", tone: "" });
      setShoppingTargetType("");
      setIsShoppingTypeMenuOpen(false);
      setIsRecognizingClothing(false);
      setIsScoringOutfits(false);
      setIsSearchingShopping(false);
      setActiveSuggestionField("");
      setActiveAppView("wardrobe");
      setIsDraggingUpload(false);
      setIsProfileOpen(false);
      setAccountProfile(null);
      setDeleteAccountConfirmation("");
      setDeleteAccountState({ busy: false, error: "" });
      setIsDeleteConfirmationOpen(false);
      removedWardrobeItemIdsRef.current.clear();
      syncingWardrobeItemIdsRef.current.clear();
    }

    function handleAuthenticated(user) {
      if (currentUser?.username !== user?.username) resetUserWorkspace();
      setCurrentUser(user);
    }

    function handleSignedOut() {
      resetUserWorkspace();
      setCurrentUser(null);
    }

    async function logout() {
      await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/auth/logout`, { method: "POST" }).catch(() => {});
      handleSignedOut();
    }

    async function openProfile() {
      setIsProfileOpen(true);
      setDeleteAccountConfirmation("");
      setDeleteAccountState({ busy: false, error: "" });
      setIsDeleteConfirmationOpen(false);
      setProfileState({ loading: true, error: "" });
      try {
        const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/auth/profile`);
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || result.detail || "Profile details could not be loaded.");
        setAccountProfile(result);
        setProfileState({ loading: false, error: "" });
      } catch (error) {
        setProfileState({ loading: false, error: error.message || "Profile details could not be loaded." });
      }
    }

    async function deleteAccount() {
      if (deleteAccountConfirmation.trim().toLowerCase() !== currentUser.username.toLowerCase()) return;
      setDeleteAccountState({ busy: true, error: "" });
      try {
        const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/auth/account`, {
          method: "DELETE",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ confirmation: deleteAccountConfirmation.trim() })
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || result.detail || "Your account could not be deleted.");
        handleSignedOut();
      } catch (error) {
        setDeleteAccountState({ busy: false, error: error.message || "Your account could not be deleted." });
      }
    }

    const RANK_LABELS = ["#1 Best Match", "#2 Runner Up", "#3 Also Good"];

    if (isCheckingSession) return createElement("div", { className: "auth-shell" },
      createElement("div", { className: "app-loader", role: "status", "aria-live": "polite" },
        createElement("div", { className: "brand-mark", "aria-hidden": "true" }, "AW"),
        createElement("div", { className: "loading-spinner", "aria-hidden": "true" }),
        createElement("strong", null, "Preparing your wardrobe"),
        createElement("span", null, "Restoring your saved pieces…")
      )
    );
    if (!currentUser) return createElement(AuthScreen, { onAuthenticated: handleAuthenticated });

    return createElement("div", { className: "app" },
      createElement("header", { className: "topbar" },
        createElement("div", { className: "brand" },
          createElement("div", { className: "brand-mark" }, "AW"),
          createElement("div", null,
            createElement("h1", null, "AI Wardrobe")
          )
        ),
        createElement("div", { className: "topbar-actions" },
          createElement("button", { className: "account-name", title: `Open ${currentUser.username}'s profile`, "aria-label": `Open ${currentUser.username}'s profile`, type: "button", onClick: openProfile },
            createElement("span", { className: "account-avatar", "aria-hidden": "true" }, currentUser.username.slice(0, 1).toUpperCase()),
            createElement("span", { className: "account-label" }, currentUser.username)
          ),
          createElement("button", {
            className: "theme-toggle",
            onClick: () => setIsDarkMode(d => !d),
            title: isDarkMode ? "Switch to light mode" : "Switch to dark mode",
            "aria-label": isDarkMode ? "Switch to light mode" : "Switch to dark mode",
            type: "button"
          }, createElement("span", { className: "theme-icon", "aria-hidden": "true" }, isDarkMode ? "☀" : "☾")),
          createElement("button", { className: "logout-button", onClick: logout, type: "button", "aria-label": "Log out" }, "Log out")
        )
      ),
      appState.text
        ? createElement("div", {
            className: `app-notice ${appState.tone}`,
            role: appState.tone === "error" ? "alert" : "status",
            "aria-live": "polite",
            "aria-atomic": "true"
          },
            createElement("span", { className: "notice-dot", "aria-hidden": "true" }),
            createElement("span", null, appState.text),
            createElement("button", { type: "button", onClick: () => setAppState({ text: "", tone: "" }), "aria-label": "Dismiss notification" }, "×")
          )
        : null,
      createElement("main", { className: "shell", "data-view": activeAppView },
        createElement("aside", { className: "left-rail" },
            createElement("nav", {
                  className: "desktop-sidebar",
                  "aria-label": "Desktop navigation"
                },
                createElement("button", {
                      type: "button",
                      className: activeAppView === "wardrobe" ? "active" : "",
                      onClick: () => setActiveAppView("wardrobe")
                    },
                    createElement("span", { "aria-hidden": "true" }, "⌂"),
                    createElement("strong", null, "My Wardrobe")
                ),

                createElement("button", {
                      type: "button",
                      className: activeAppView === "add" ? "active" : "",
                      onClick: () => setActiveAppView("add")
                    },
                    createElement("span", { "aria-hidden": "true" }, "+"),
                    createElement("strong", null, "Add Item")
                ),

                createElement("button", {
                      type: "button",
                      className: activeAppView === "match" ? "active" : "",
                      onClick: () => setActiveAppView("match")
                    },
                    createElement("span", { "aria-hidden": "true" }, "✦"),
                    createElement("strong", null, "Outfit Match")
                )
            ),
          createElement("section", { className: "panel upload-box app-screen add-screen" },
            createElement("div", null,
              createElement("h2", null, editingWardrobeItemId ? "Edit item" : "Add item")
            ),
            selectedImagePreview
              ? createElement("div", { className: "preview" },
                  createElement("button", { className: "icon-button cancel-upload", onClick: cancelSelectedUpload, type: "button", title: "Cancel upload" }, "x"),
                  createElement("img", { src: selectedImagePreview, alt: "Selected clothing preview" })
                )
              : createElement("label", {
                  className: `dropzone${isDraggingUpload ? " dragging" : ""}`,
                  onDragEnter: (event) => { event.preventDefault(); if (!bulkUploadState.active) setIsDraggingUpload(true); },
                  onDragOver: (event) => event.preventDefault(),
                  onDragLeave: (event) => { if (!event.currentTarget.contains(event.relatedTarget)) setIsDraggingUpload(false); },
                  onDrop: handleClothingFilesDropped
                },
                  createElement("input", { type: "file", accept: "image/png,image/jpeg,image/webp", multiple: true, disabled: bulkUploadState.active, onChange: handleClothingImageSelected }),
                  createElement("span", { className: "dropzone-icon", "aria-hidden": "true" }, "+"),
                  createElement("strong", null, bulkUploadState.active ? "Uploading wardrobe" : "Choose images"),
                  createElement("span", null, `Drop images here, or select one to review and up to ${MAX_BULK_UPLOAD_FILES} to add automatically`),
                  createElement("small", null, "PNG, JPG or WebP · 15 MB max each")
                ),
            uploadState.text ? createElement("p", { className: "status " + uploadState.tone, role: uploadState.tone === "error" ? "alert" : "status", "aria-live": "polite" }, uploadState.text) : null,
            bulkUploadState.results.length
              ? createElement("div", { className: "bulk-results", role: "status", "aria-live": "polite" },
                  createElement("div", { className: "bulk-results-head" },
                    createElement("span", null,
                      createElement("strong", null, bulkUploadState.active ? "Uploading wardrobe" : "Upload complete"),
                      createElement("small", null, `${bulkUploadState.current} of ${bulkUploadState.total} processed`)
                    ),
                    !bulkUploadState.active ? createElement("button", { type: "button", className: "auth-switch", onClick: () => setBulkUploadState({ active: false, current: 0, total: 0, results: [] }) }, "Clear") : null
                  ),
                  createElement("div", { className: "bulk-progress", "aria-hidden": "true" },
                    createElement("span", { style: { width: `${bulkUploadState.total ? (bulkUploadState.current / bulkUploadState.total) * 100 : 0}%` } })
                  ),
                  createElement("ul", null, bulkUploadState.results.map((result, index) =>
                    createElement("li", { key: index, className: `bulk-${result.status}` },
                      createElement("span", null, result.message))
                  ))
                )
              : null,
            selectedImagePreview ? createElement("div", { className: "recognition" },
              createElement("div", { className: "data-grid" },
                renderEditableAnalysisField("Name", "name", recognizedClothingDetails.name, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Category", "category", recognizedClothingDetails.category, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Color", "color", recognizedClothingDetails.color, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Material", "material", recognizedClothingDetails.material, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Pattern", "pattern", recognizedClothingDetails.pattern, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Occasion", "occasion", recognizedClothingDetails.occasion, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Season", "season", recognizedClothingDetails.season, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField)
              )
            ) : null,
            selectedImagePreview ? createElement("div", { className: "actions" },
              createElement("button", { className: "primary", onClick: saveRecognizedClothingItem, disabled: isRecognizingClothing || !selectedImagePreview || !recognizedClothingDetails.name, type: "button" }, editingWardrobeItemId ? "Save changes" : "Save item"),
              createElement("button", { className: "secondary", onClick: cancelSelectedUpload, disabled: !selectedImagePreview && !recognizedClothingDetails.name, type: "button" }, "Cancel")
            ) : null
          ),
        ),
        createElement("section", { className: "main" },
          createElement("section", { className: "panel app-screen wardrobe-screen" },
            createElement("div", { className: "wardrobe-head" },
              createElement("div", { className: "section-title" },
                createElement("h2", null, "Wardrobe"),
                createElement("p", null, isWardrobeHydrated
                  ? `${wardrobeItems.length} ${wardrobeItems.length === 1 ? "piece" : "pieces"} · select one to style`
                  : "Restoring saved pieces…")
              ),
              createElement("div", { className: "wardrobe-controls" },
                createElement("div", { className: "filters" },
                  renderWardrobeFilterButton("all", activeWardrobeFilter, setActiveWardrobeFilter, wardrobeItems.length),
                  renderWardrobeFilterButton("top", activeWardrobeFilter, setActiveWardrobeFilter, topItemCount),
                  renderWardrobeFilterButton("bottom", activeWardrobeFilter, setActiveWardrobeFilter, bottomItemCount),
                  renderWardrobeFilterButton("dress", activeWardrobeFilter, setActiveWardrobeFilter, dressItemCount)
                ),
                createElement("div", { className: "sample-actions" },
                  createElement("button", { className: "secondary", onClick: loadSampleWardrobeItems, disabled: missingSampleCount === 0, type: "button" }, missingSampleCount ? "Add samples" : "Samples added"),
                  createElement("button", { className: "danger", onClick: clearWardrobe, disabled: sampleItemCount === 0, type: "button" }, "Clear samples")
                )
                ,
                createElement("div", { className: "shopping-fallback" },
                  createElement("p", null, "Not feeling these matches? Shop complementary options online."),
                  renderShoppingTypeSelector(selectedWardrobeItem, shoppingTargetType, setShoppingTargetType, isShoppingTypeMenuOpen, setIsShoppingTypeMenuOpen),
                  createElement("button", {
                    className: "secondary",
                    type: "button",
                    onClick: findShoppingOptions,
                    disabled: isSearchingShopping || !shoppingTargetType
                  }, isSearchingShopping ? "Searching stores..." : "Shop online"),
                  shoppingStatus.text ? createElement("p", { className: "status " + shoppingStatus.tone }, shoppingStatus.text) : null,
                  shoppingOptions.length
                    ? createElement("div", { className: "shopping-results" }, shoppingOptions.map((option, index) =>
                        createElement("a", {
                          className: "shopping-card",
                          href: option.url,
                          target: "_blank",
                          rel: "noopener noreferrer",
                          key: option.url || index
                        },
                          option.image ? createElement("img", { src: option.image, alt: "", loading: "lazy", decoding: "async" }) : null,
                          createElement("span", null,
                            createElement("strong", null, option.title),
                            createElement("small", null, [option.price, option.store].filter(Boolean).join(" · "))
                          ),
                          createElement("b", null, "Buy")
                        )
                      ))
                    : null
                )
              )
            ),
            displayedWardrobeItems.length
              ? createElement("div", { className: "wardrobe-grid", "aria-busy": isScoringOutfits }, displayedWardrobeItems.map((item, itemIndex) =>
                  createElement("article", {
                    key: item.id,
                    className: "item-card "
                      + (item.id === selectedWardrobeItemId ? "selected " : "")
                      + (isScoringOutfits ? "selection-locked" : ""),
                    style: { "--item-delay": `${Math.min(itemIndex, 10) * 35}ms` }
                  },
                    createElement("button", {
                      className: "icon-button remove-item",
                      type: "button",
                      title: `Remove ${item.analysis.name}`,
                      "aria-label": `Remove ${item.analysis.name}`,
                      onClick: (event) => removeWardrobeItem(item.id, event)
                    }, "x"),
                    createElement("button", {
                      className: "icon-button edit-item-icon",
                      type: "button",
                      title: `Edit ${item.analysis.name}`,
                      "aria-label": `Edit ${item.analysis.name}`,
                      onClick: (event) => editWardrobeItem(item, event)
                    }, "✎"),
                    createElement("button", {
                      className: "item-select",
                      type: "button",
                      disabled: isScoringOutfits,
                      title: isScoringOutfits ? "Wait for matching to finish" : `Style ${item.analysis.name}`,
                      onClick: () => selectWardrobeItem(item.id),
                      "aria-label": isScoringOutfits
                        ? `${item.analysis.name} is unavailable while matching`
                        : `Style ${item.analysis.name}`
                    },
                      createElement("img", { src: item.image, alt: item.analysis.name, loading: "lazy", decoding: "async" }),
                      createElement("div", { className: "item-info" },
                        createElement("strong", null, item.analysis.name),
                        createElement("div", { className: "chips" },
                          createElement("span", { className: "chip" }, item.category),
                          createElement("span", { className: "chip" }, item.analysis.color || item.analysis.primaryColor || "color"),
                          createElement("span", { className: "chip" }, item.analysis.material || "material"),
                          createElement("span", { className: "chip" }, item.analysis.pattern || "pattern"),
                          createElement("span", { className: "chip" }, item.analysis.occasion || "occasion"),
                          createElement("span", { className: "chip" }, item.analysis.season || "season")
                        )
                      )
                    )
                  )
                ))
              : createElement("div", { className: "empty empty-wardrobe" },
                  createElement("span", { className: "empty-icon", "aria-hidden": "true" }, "✦"),
                  createElement("h3", null, activeWardrobeFilter === "all" ? "Build your digital closet" : `No ${activeWardrobeFilter} pieces yet`),
                  createElement("p", null, activeWardrobeFilter === "all" ? "Add clothing photos for AI details and instant outfit ideas." : "Switch filters or add a piece in this category.")
                )
          ),
          createElement("section", { className: "score-panel app-screen match-screen", ref: matcherPanelRef },
            createElement("div", { className: "score-head" },
              createElement("div", null,
                createElement("h2", null, "Matching")
              ),
              createElement("span", {
                className: "match-button-wrap " + (selectedWardrobeItem && !outfitCandidateItems.length ? "show-no-alternatives" : ""),
                "data-tooltip": selectedWardrobeItem && !outfitCandidateItems.length ? "No alternatives found" : "",
                tabIndex: selectedWardrobeItem && !outfitCandidateItems.length ? 0 : -1
              },
                createElement("button", { className: "primary", onClick: () => analyzeSelectedItemOutfitScores(), disabled: !selectedWardrobeItem || !outfitCandidateItems.length || isScoringOutfits, type: "button" },
                  isScoringOutfits ? "Matching..." : "Match"
                )
              )
            ),
            selectedWardrobeItem
              ? createElement("div", { className: "selected-item-preview" },
                  createElement("div", { className: "selected-item-image-wrap" },
                    createElement("img", { src: selectedWardrobeItem.image, alt: selectedWardrobeItem.analysis.name, decoding: "async" })
                  ),
                  createElement("div", { className: "selected-item-summary" },
                    createElement("span", { className: "selected-item-badge" }, "Styling"),
                    createElement("strong", null, selectedWardrobeItem.analysis.name),
                    createElement("span", null, [selectedWardrobeItem.analysis.color, selectedWardrobeItem.analysis.occasion].filter(Boolean).join(" · "))
                  )
                )
              : null,
            outfitScoreResults.length
              ? createElement("div", { className: "top3-results" },
                  outfitScoreResults.map((result, index) =>
                    createElement("div", { key: result.candidateId, className: "match-card", style: { "--item-delay": `${index * 80}ms` } },
                      createElement("div", { className: "match-rank" }, RANK_LABELS[index] || `#${index + 1}`),
                      createElement("img", { className: "match-candidate-image", src: result.candidate.image, alt: result.candidate.analysis.name, loading: "lazy", decoding: "async" }),
                      createElement("div", { className: "match-info" },
                        createElement("strong", null, result.candidate.analysis.name),
                        result.verdict && createElement("p", { className: "match-verdict" }, result.verdict)
                      ),
                      createElement("div", { className: "score-number", style: { "--score": result.score } }, `${result.score}%`)
                    )
                  ),
                  createElement("div", { className: "shopping-fallback match-shopping-fallback" },
                    createElement("p", null, "Not feeling these matches? Shop complementary options online."),
                    renderShoppingTypeSelector(selectedWardrobeItem, shoppingTargetType, setShoppingTargetType, isShoppingTypeMenuOpen, setIsShoppingTypeMenuOpen),
                    createElement("button", {
                    className: "secondary",
                    type: "button",
                    onClick: findShoppingOptions,
                    disabled: isSearchingShopping || !shoppingTargetType
                  }, isSearchingShopping ? "Searching stores..." : "Shop online"),
                    shoppingStatus.text ? createElement("p", { className: "status " + shoppingStatus.tone }, shoppingStatus.text) : null,
                    shoppingOptions.length
                      ? createElement("div", { className: "shopping-results" }, shoppingOptions.map((option, index) =>
                          createElement("a", {
                            className: "shopping-card",
                            href: option.url,
                            target: "_blank",
                            rel: "noopener noreferrer",
                            key: option.url || index
                          },
                            option.image ? createElement("img", { src: option.image, alt: "", loading: "lazy", decoding: "async" }) : null,
                            createElement("span", null,
                              createElement("strong", null, option.title),
                              createElement("small", null, [option.price, option.store].filter(Boolean).join(" · "))
                            ),
                            createElement("b", null, "Buy")
                          )
                        ))
                      : null
                  )
                )
              : isScoringOutfits
                ? createElement("div", { className: "empty match-loading" },
                    createElement("div", { className: "loading-spinner", "aria-hidden": "true" }),
                    createElement("h3", null, "Finding your best combinations"),
                    createElement("p", null, `Comparing ${outfitCandidateItems.length} ${outfitCandidateItems.length === 1 ? "piece" : "pieces"} for color, texture and occasion.`)
                  )
                : selectedWardrobeItem
                  ? createElement("div", { className: "match-ready" },
                      createElement("span", { "aria-hidden": "true" }, "✦"),
                      createElement("p", null, outfitCandidateItems.length ? "Ready to find the strongest outfits in your wardrobe." : "Add a complementary top or bottom to unlock matching.")
                    )
                  : createElement("div", { className: "empty" },
                      createElement("span", { className: "empty-icon", "aria-hidden": "true" }, "◇"),
                      createElement("h3", null, "Choose a piece to style"),
                      createElement("p", null, "Select any top or bottom from your wardrobe and AI will rank its best matches."),
                      createElement("button", { className: "secondary", type: "button", onClick: () => setActiveAppView("wardrobe") }, "Open wardrobe")
                    )
          )
        )
      ),
      createElement("nav", { className: "app-tabs", "aria-label": "Primary" },
        renderAppTabButton("add", "Add", activeAppView, setActiveAppView),
        renderAppTabButton("wardrobe", "Wardrobe", activeAppView, setActiveAppView),
        renderAppTabButton("match", "Match", activeAppView, setActiveAppView)
      ),
      isProfileOpen ? createElement("div", { className: "profile-overlay", role: "presentation", onMouseDown: (event) => { if (event.target === event.currentTarget && !deleteAccountState.busy) setIsProfileOpen(false); } },
        createElement("section", { className: "profile-dialog", role: "dialog", "aria-modal": "true", "aria-labelledby": "profile-title", ref: profileDialogRef, "data-busy": deleteAccountState.busy ? "true" : "false" },
          createElement("div", { className: "profile-dialog-head" },
            createElement("div", null, createElement("h2", { id: "profile-title" }, "Profile"), createElement("p", null, `Signed in as ${currentUser.username}`)),
            createElement("button", { className: "icon-button", type: "button", title: "Close profile", "aria-label": "Close profile", disabled: deleteAccountState.busy, onClick: () => setIsProfileOpen(false) }, "x")
          ),
          profileState.loading ? createElement("p", { className: "status busy" }, "Loading profile...") : null,
          profileState.error ? createElement("p", { className: "status error", role: "alert" }, profileState.error) : null,
          accountProfile && !profileState.loading ? createElement("div", { className: "profile-details" },
            createElement("div", null, createElement("span", null, "Username"), createElement("strong", null, accountProfile.username)),
            createElement("div", null, createElement("span", null, "Email"), createElement("strong", null, accountProfile.email)),
            createElement("div", null, createElement("span", null, "Password"), createElement("strong", { className: "masked-password" }, accountProfile.passwordSet ? "••••••••••••" : "Not set")),
            createElement("div", null, createElement("span", null, "Sign-in method"), createElement("strong", null, accountProfile.signInMethod))
          ) : null,
          !isDeleteConfirmationOpen ? createElement("button", {
            className: "danger profile-delete-trigger",
            type: "button",
            onClick: () => setIsDeleteConfirmationOpen(true)
          }, "Delete account") : null,
          isDeleteConfirmationOpen ? createElement("div", { className: "delete-account-zone" },
            createElement("h3", null, "Delete account"),
            createElement("p", null, "Deletes account and all of your wardrobe items will be removed permanently."),
            createElement("label", null,
              createElement("span", null, `Type ${currentUser.username} to confirm`),
              createElement("input", { value: deleteAccountConfirmation, disabled: deleteAccountState.busy, autoComplete: "off", onChange: (event) => { setDeleteAccountConfirmation(event.target.value); setDeleteAccountState({ busy: false, error: "" }); } })
            ),
            deleteAccountState.error ? createElement("p", { className: "status error", role: "alert" }, deleteAccountState.error) : null,
            createElement("div", { className: "delete-account-actions" },
              createElement("button", { className: "secondary", type: "button", disabled: deleteAccountState.busy, onClick: () => { setIsDeleteConfirmationOpen(false); setDeleteAccountConfirmation(""); setDeleteAccountState({ busy: false, error: "" }); } }, "Cancel"),
              createElement("button", { className: "danger delete-account-button", type: "button", onClick: deleteAccount, disabled: deleteAccountState.busy || deleteAccountConfirmation.trim().toLowerCase() !== currentUser.username.toLowerCase() }, deleteAccountState.busy ? "Deleting account and R2 files..." : "Delete")
            )
          ) : null
        )
      ) : null
    );
  }

  function resolveBackendApiBaseUrl() {
    const pageHostname = window.location.hostname;
    if (!pageHostname || pageHostname === "localhost" || pageHostname === "127.0.0.1") {
      return "http://127.0.0.1:8080";
    }

    const configuredUrl = trimText(
      window.AI_WARDROBE_API_URL ||
      localStorage.getItem("ai-wardrobe-api-url")
    );
    if (configuredUrl) return configuredUrl.replace(/\/+$/, "");

    return `http://${pageHostname}:8080`;
  }

  function renderAppTabButton(viewName, label, activeView, setActiveView) {
    const icons = { add: "+", wardrobe: "▦", match: "✦" };
    return createElement("button", {
      type: "button",
      className: activeView === viewName ? "active" : "",
      onClick: () => setActiveView(viewName),
      "aria-current": activeView === viewName ? "page" : undefined
    },
      createElement("span", { className: "tab-icon", "aria-hidden": "true" }, icons[viewName]),
      createElement("span", null, label)
    );
  }

  function renderEditableAnalysisField(label, fieldName, value, onChange, activeField, setActiveField) {
    const suggestions = FIELD_SUGGESTIONS[fieldName] || [];
    const normalizedValue = trimText(value).toLowerCase();
    const matchingSuggestions = normalizedValue
      ? suggestions.filter((suggestion) => suggestion.includes(normalizedValue) && suggestion !== normalizedValue).slice(0, 5)
      : [];
    return createElement("label", { className: "field" },
      createElement("span", null, label),
      createElement("input", {
        value: value || "",
        autoComplete: "off",
        onFocus: () => setActiveField(fieldName),
        onBlur: () => setActiveField(""),
        onChange: (event) => {
          setActiveField(fieldName);
          onChange(fieldName, event.target.value);
        }
      }),
      activeField === fieldName && matchingSuggestions.length
        ? createElement("div", { className: "field-suggestions" },
            matchingSuggestions.map((suggestion) =>
              createElement("button", {
                type: "button",
                key: suggestion,
                onMouseDown: (event) => {
                  event.preventDefault();
                  onChange(fieldName, suggestion);
                  setActiveField("");
                }
              }, suggestion)
            )
          )
        : null
    );
  }

  function renderWardrobeFilterButton(filterValue, activeFilter, setActiveFilter, count) {
    const filterLabel = filterValue === "all" ? "All" : filterValue === "top" ? "Tops" : filterValue === "bottom" ? "Bottoms" : "Dresses";
    return createElement("button", {
      type: "button",
      className: activeFilter === filterValue ? "active" : "",
      onClick: () => setActiveFilter(filterValue),
      "aria-pressed": activeFilter === filterValue
    }, `${filterLabel} (${count})`);
  }

  function renderShoppingTypeSelector(selectedItem, value, onChange, isOpen, setIsOpen) {
    if (!selectedItem) return null;
    const options = selectedItem.category === "bottom"
      ? [
          ["", "Choose a top type"],
          ["blouse", "Blouse"],
          ["shirt", "Shirt"],
          ["t-shirt", "T-shirt"],
          ["any-top", "Any top"]
        ]
      : [
          ["", "Choose a bottom type"],
          ["pants", "Pants"],
          ["skirt", "Skirt"],
          ["shorts", "Shorts"],
          ["any-bottom", "Any bottom"]
        ];
    const selectedOption = options.find(([optionValue]) => optionValue === value);
    return createElement("div", { className: "shopping-type-field" },
      createElement("span", null, "What would you like to wear?"),
      createElement("button", {
        className: "shopping-type-trigger",
        type: "button",
        "aria-expanded": isOpen,
        onClick: () => setIsOpen(!isOpen)
      },
        createElement("span", null, selectedOption ? selectedOption[1] : options[0][1]),
        createElement("b", null, isOpen ? "▲" : "▼")
      ),
      isOpen
        ? createElement("div", { className: "shopping-type-menu" },
            options.slice(1).map(([optionValue, label]) =>
              createElement("button", {
                className: optionValue === value ? "active" : "",
                type: "button",
                key: optionValue,
                onClick: () => {
                  onChange(optionValue);
                  setIsOpen(false);
                }
              }, label)
            )
          )
        : null
    );
  }

  function normalizeClothingAnalysis(result) {
    result = result || {};
    const normalizedName = trimText(result.name) || "Recognized clothing item";
    const normalizedCategory = normalizeCategory(result.category);
    return Object.assign({}, EMPTY_CLOTHING_ANALYSIS, {
      name: normalizedName,
      color: trimText(result.color || result.primaryColor),
      category: normalizedCategory,
      pattern: trimText(result.pattern),
      material: trimText(result.material),
      occasion: trimText(result.occasion),
      season: trimText(result.season)
    });
  }

  function normalizeCategory(value) {
    const category = trimText(value).toLowerCase();
    if (["dress", "dresses", "jumpsuit", "romper", "one-piece", "one piece"].includes(category)) {
      return "dress";
    }
    if (["bottom", "bottoms", "pant", "pants", "jean", "jeans", "trouser", "trousers", "skirt", "short", "shorts"].includes(category)) {
      return "bottom";
    }
    if (["top", "tops", "shirt", "tshirt", "t-shirt", "tee", "blouse", "hoodie", "sweatshirt", "tank", "tank top", "jacket", "cardigan"].includes(category)) {
      return "top";
    }
    return category || "top";
  }

  function canItemsCreateOutfitPair(firstItem, secondItem) {
    return (firstItem.category === "top" && secondItem.category === "bottom") || (firstItem.category === "bottom" && secondItem.category === "top");
  }

  function buildWardrobeItemDescription(item) {
    const details = item.analysis || {};
    return [
      details.name,
      `category: ${details.category}`,
      `color: ${details.color || details.primaryColor}`,
      `pattern: ${details.pattern}`,
      `material: ${details.material}`,
      `occasion: ${details.occasion}`,
      `season: ${details.season}`
    ].filter(Boolean).join("; ");
  }

  function shortlistOutfitCandidates(selectedItem, candidates) {
    const selected = selectedItem.analysis || {};
    return candidates.slice().sort((first, second) =>
      outfitShortlistScore(selected, second.analysis || {}) - outfitShortlistScore(selected, first.analysis || {}));
  }

  function outfitShortlistScore(selected, candidate) {
    const selectedText = Object.values(selected).join(" ").toLowerCase();
    const candidateText = Object.values(candidate).join(" ").toLowerCase();
    let score = 50;
    const polishedTerms = ["tailored", "fitted", "straight", "pencil", "blouse", "satin", "silk", "linen", "ribbed"];
    const casualPenaltyTerms = ["cutout", "mesh", "sheer", "distressed", "graphic", "athletic", "lounge", "tube"];
    if (selected.occasion && selected.occasion === candidate.occasion) score += 12;
    if ([selected.occasion, candidate.occasion].every((value) => ["smart casual", "work", "formal"].includes(value))) score += 8;
    if (selected.season === "all season" || candidate.season === "all season" || selected.season === candidate.season) score += 6;
    if (selected.pattern && selected.pattern !== "solid" && candidate.pattern === "solid") score += 8;
    if (polishedTerms.some((term) => candidateText.includes(term))) score += 8;
    if (casualPenaltyTerms.some((term) => candidateText.includes(term))) score -= 18;
    if (["peplum", "puff", "ruffle", "oversized", "voluminous"].some((term) => selectedText.includes(term))) {
      if (["straight", "tapered", "slim", "pencil", "tailored"].some((term) => candidateText.includes(term))) score += 12;
      if (["wide-leg", "palazzo", "baggy", "balloon"].some((term) => candidateText.includes(term))) score -= 14;
    }
    return score;
  }

  function normalizeWardrobeItem(item) {
    item = item || {};
    const rawAnalysis = item.analysis || {};
    const analysis = normalizeClothingAnalysis(Object.assign({}, rawAnalysis, {
      category: rawAnalysis.category || item.category
    }));
    return Object.assign({}, item, {
      analysis,
      category: analysis.category
    });
  }

  function clampOutfitScore(value) {
    const score = Math.round(Number(value));
    if (Number.isNaN(score)) return 0;
    return Math.max(0, Math.min(100, score));
  }

  function trimText(value) {
    return String(value || "").trim();
  }

  function createWardrobeItemId() {
    if (window.crypto?.randomUUID) return `item_${window.crypto.randomUUID()}`;
    return `item_${Date.now()}_${Math.random().toString(16).slice(2)}`;
  }

  function createSampleKeyFromName(name) {
    const knownSampleKey = SAMPLE_KEY_BY_NAME.get(normalizeDuplicateText(name));
    if (knownSampleKey) return knownSampleKey;
    return `sample-${normalizeDuplicateText(name).replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "")}`;
  }

  function normalizeDuplicateText(value) {
    return String(value || "").trim().toLowerCase().replace(/\s+/g, " ");
  }

  function deduplicateWardrobeItems(items) {
    const seenKeys = new Set();
    return items.filter((item) => {
      const duplicateKeys = getWardrobeDuplicateKeys(item);
      if (!duplicateKeys.length) return true;
      if (duplicateKeys.some((key) => seenKeys.has(key))) return false;
      duplicateKeys.forEach((key) => seenKeys.add(key));
      return true;
    });
  }

  function findDuplicateWardrobeItem(items, candidateItem) {
    const candidateKeys = getWardrobeDuplicateKeys(candidateItem);
    if (!candidateKeys.length) return null;

    return items.find((item) => {
      const existingKeys = getWardrobeDuplicateKeys(item);
      return candidateKeys.some((key) => existingKeys.includes(key));
    }) || null;
  }

  function isSampleWardrobeItem(item) {
    return getWardrobeDuplicateKeys(item).some((key) => key.startsWith("sample:"));
  }

  function getWardrobeDuplicateKeys(item) {
    const keys = [];
    const knownSampleKey = SAMPLE_KEY_BY_NAME.get(normalizeDuplicateText(item.analysis?.name));
    if (item.sampleKey) keys.push(`sample:${item.sampleKey}`);
    if (knownSampleKey) keys.push(`sample:${knownSampleKey}`);
    if (item.originalFileName === "sample" && item.analysis?.name) keys.push(`sample:${createSampleKeyFromName(item.analysis.name)}`);
    if (item.imageFingerprint) keys.push(`image:${item.imageFingerprint}`);
    else if (item.image) keys.push(`image-data:${item.image}`);
    return keys;
  }

  async function readImageFileAsOptimizedDataUrl(file) {
    validateImageFile(file);
    if (window.createImageBitmap) {
      try {
        const bitmap = await window.createImageBitmap(file);
        try {
          return await drawOptimizedImage(bitmap, bitmap.width, bitmap.height, MAX_ANALYSIS_IMAGE_DIMENSION, ANALYSIS_IMAGE_QUALITY);
        } finally {
          bitmap.close?.();
        }
      } catch (error) {
        // Some older browsers expose createImageBitmap but cannot decode all camera images.
      }
    }
    const originalDataUrl = await readFileAsDataUrl(file);
    return resizeImageDataUrl(originalDataUrl, MAX_ANALYSIS_IMAGE_DIMENSION, ANALYSIS_IMAGE_QUALITY);
  }

  function validateImageFile(file) {
    if (!file?.type?.startsWith("image/") || !["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
      throw new Error("Choose a PNG, JPG, or WebP image.");
    }
    if (file.size > MAX_IMAGE_FILE_BYTES) {
      throw new Error("Each image must be 15 MB or smaller.");
    }
  }

  function readFileAsDataUrl(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = () => reject(new Error("Could not read this file."));
      reader.readAsDataURL(file);
    });
  }

  function resizeImageDataUrl(sourceDataUrl, maxDimension, imageQuality) {
    return new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => drawOptimizedImage(image, image.width, image.height, maxDimension, imageQuality).then(resolve, reject);
      image.onerror = () => reject(new Error("Could not optimize this image."));
      image.src = sourceDataUrl;
    });
  }

  function drawOptimizedImage(image, imageWidth, imageHeight, maxDimension, imageQuality) {
    const scale = Math.min(1, maxDimension / Math.max(imageWidth, imageHeight));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(imageWidth * scale));
    canvas.height = Math.max(1, Math.round(imageHeight * scale));
    const canvasContext = canvas.getContext("2d", { alpha: false });
    if (!canvasContext) return Promise.reject(new Error("Could not prepare this image."));
    canvasContext.fillStyle = "#f8fafc";
    canvasContext.fillRect(0, 0, canvas.width, canvas.height);
    canvasContext.drawImage(image, 0, 0, canvas.width, canvas.height);
    return new Promise((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (!blob) {
          reject(new Error("Could not optimize this image."));
          return;
        }
        readFileAsDataUrl(blob).then(resolve, reject);
      }, "image/jpeg", imageQuality);
    });
  }

  async function createImageFingerprint(imageDataUrl) {
    if (!window.crypto?.subtle) {
      return imageDataUrl;
    }

    const encodedImage = new TextEncoder().encode(imageDataUrl);
    const digest = await window.crypto.subtle.digest("SHA-256", encodedImage);
    return Array.from(new Uint8Array(digest))
      .map((byte) => byte.toString(16).padStart(2, "0"))
      .join("");
  }

  async function sendJsonToBackend(endpointPath, payload) {
    const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}${endpointPath}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(result.error || result.detail || "Request failed.");
    }
    return result;
  }

  async function fetchStoredWardrobeItems() {
    const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/wardrobe-items`);
    const result = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(result.error || result.detail || "Could not load wardrobe items from R2.");
    }
    return Array.isArray(result) ? result : [];
  }

  async function authenticatedFetch(url, options) {
    options = Object.assign({}, options, { credentials: "include" });
    const method = String(options.method || "GET").toUpperCase();
    const isMutation = !["GET", "HEAD", "OPTIONS"].includes(method);
    if (isMutation) {
      options.headers = Object.assign({}, options.headers, { "X-XSRF-TOKEN": await getCsrfToken() });
    }
    let response = await fetchWithTimeout(url, options);
    if (isMutation && response.status === 403) {
      csrfTokenPromise = null;
      options.headers = Object.assign({}, options.headers, { "X-XSRF-TOKEN": await getCsrfToken() });
      response = await fetchWithTimeout(url, options);
    }
    if (response.status === 401 && !url.includes("/api/auth/")) window.location.reload();
    return response;
  }

  function getCsrfToken() {
    if (!csrfTokenPromise) {
      csrfTokenPromise = fetchWithTimeout(`${BACKEND_API_BASE_URL}/api/auth/csrf`, { credentials: "include" })
        .then(async (response) => {
          const csrf = await response.json().catch(() => ({}));
          if (!response.ok || !csrf.token) throw new Error("Could not start a secure request. Please try again.");
          return csrf.token;
        })
        .catch((error) => {
          csrfTokenPromise = null;
          throw error;
        });
    }
    return csrfTokenPromise;
  }

  async function fetchWithTimeout(url, options) {
    const requestOptions = Object.assign({}, options);
    const timeoutController = new AbortController();
    const upstreamSignal = requestOptions.signal;
    const forwardAbort = () => timeoutController.abort(upstreamSignal?.reason);
    if (upstreamSignal) {
      if (upstreamSignal.aborted) forwardAbort();
      else upstreamSignal.addEventListener("abort", forwardAbort, { once: true });
    }
    const timeoutId = window.setTimeout(() => timeoutController.abort("timeout"), REQUEST_TIMEOUT_MS);
    requestOptions.signal = timeoutController.signal;
    try {
      return await fetch(url, requestOptions);
    } catch (error) {
      if (timeoutController.signal.aborted && !upstreamSignal?.aborted) {
        throw new Error("The request took too long. Please try again.");
      }
      throw error;
    } finally {
      window.clearTimeout(timeoutId);
      upstreamSignal?.removeEventListener?.("abort", forwardAbort);
    }
  }

  function AuthScreen(props) {
    const [mode, setMode] = React.useState(() => {
      const params = new URLSearchParams(window.location.search);
      return params.get("resetToken") ? "reset" : params.get("googleOnboarding") === "required" ? "googleSetup" : "login";
    });
    const [username, setUsername] = React.useState("");
    const [email, setEmail] = React.useState("");
    const [password, setPassword] = React.useState("");
    const [confirmPassword, setConfirmPassword] = React.useState("");
    const [humanAnswer, setHumanAnswer] = React.useState("");
    const [humanCheck, setHumanCheck] = React.useState(() => ({ left: Math.ceil(Math.random() * 9), right: Math.ceil(Math.random() * 9) }));
    const [status, setStatus] = React.useState("");
    const [statusTone, setStatusTone] = React.useState("error");
    const [busy, setBusy] = React.useState(false);

    const normalizedPassword = password.toLowerCase();
    const normalizedUsername = username.trim().toLowerCase();
    const emailName = email.trim().toLowerCase().split("@")[0];
    const passwordChecks = [
      { label: "At least 8 characters", valid: password.length >= 8 },
      { label: "Includes a letter", valid: /[A-Za-z]/.test(password) },
      { label: "Includes a number", valid: /[0-9]/.test(password) },
      { label: "Includes a special character", valid: /[^A-Za-z0-9\s]/.test(password) },
      { label: "Does not contain your username", valid: normalizedUsername.length < 3 || !normalizedPassword.includes(normalizedUsername) },
      { label: "Does not contain your email name", valid: emailName.length < 3 || !normalizedPassword.includes(emailName) },
    ];

    React.useEffect(() => {
      const params = new URLSearchParams(window.location.search);
      const token = params.get("verifyToken");
      const resetToken = params.get("resetToken");
      const googleError = params.get("googleError");
      const googleOnboarding = params.get("googleOnboarding");
      if (googleOnboarding === "required") {
        setMode("googleSetup");
        setBusy(true);
        fetch(`${BACKEND_API_BASE_URL}/api/auth/google/pending`, { credentials: "include" })
          .then((response) => response.json())
          .then((result) => {
            if (!result.pending) throw new Error("Your Google setup session expired. Continue with Google again.");
            setEmail(result.email || "");
          })
          .catch((error) => setStatus(error.message))
          .finally(() => setBusy(false));
      }
      if (googleError) {
        setStatusTone("error");
        setStatus(googleError === "email_not_verified" ? "Google did not provide a verified email address." : "Google sign-in could not be completed.");
      }
      if (resetToken) setMode("reset");
      if (!token) return;
      setBusy(true);
      authenticatedFetch(`${BACKEND_API_BASE_URL}/api/auth/verify`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token })
      }).then(async (response) => {
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || result.detail || "Email verification failed.");
        setMode("login"); setStatusTone("success"); setStatus(result.message || "Email verified. You can now log in.");
      }).catch((error) => { setStatusTone("error"); setStatus(error.message); })
        .finally(() => {
          setBusy(false);
          window.history.replaceState({}, document.title, window.location.pathname);
        });
    }, []);

    async function submit(event) {
      event.preventDefault(); setBusy(true); setStatus(""); setStatusTone("error");
      if (mode === "forgot") {
        try {
          const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/auth/forgot-password`, {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email })
          });
          const result = await response.json().catch(() => ({}));
          if (!response.ok) throw new Error(result.detail || result.error || "Could not request a reset link.");
          setStatusTone("success"); setStatus(result.message);
        } catch (error) { setStatus(error.message); } finally { setBusy(false); }
        return;
      }
      if ((mode === "register" || mode === "googleSetup" || mode === "reset") && password !== confirmPassword) { setStatus("Passwords do not match."); setBusy(false); return; }
      if (mode === "register" || mode === "googleSetup" || mode === "reset") {
        const failedCheck = passwordChecks.find((check) => !check.valid);
        if (failedCheck) { setStatus(failedCheck.label + "."); setBusy(false); return; }
      }
      try {
        if (mode === "reset") {
          const resetToken = new URLSearchParams(window.location.search).get("resetToken") || "";
          const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/auth/reset-password`, {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ token: resetToken, password, confirmPassword })
          });
          const result = await response.json().catch(() => ({}));
          if (!response.ok) throw new Error(result.detail || result.error || "Could not reset your password.");
          window.history.replaceState({}, document.title, window.location.pathname);
          setMode("login"); setPassword(""); setConfirmPassword(""); setStatusTone("success"); setStatus(result.message);
          return;
        }
        const endpoint = mode === "googleSetup" ? "google/complete" : mode;
        const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/auth/${endpoint}`, {
          method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(mode === "register"
            ? { username, email, password, confirmPassword, humanLeft: humanCheck.left, humanRight: humanCheck.right, humanAnswer: Number(humanAnswer) }
            : mode === "googleSetup" ? { username, password, confirmPassword } : { username, password })
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.detail || result.error || "Could not sign in.");
        if (mode === "register") {
          setMode("login"); setPassword(""); setConfirmPassword(""); setStatusTone("success");
          setStatus("Account created. Check your email and open the verification link before logging in.");
        } else {
          if (mode === "googleSetup") window.history.replaceState({}, document.title, window.location.pathname);
          props.onAuthenticated(result);
        }
      } catch (error) { setStatus(error.message); } finally { setBusy(false); }
    }

    async function startGoogleLogin() {
      setStatus("");
      try {
        const response = await fetch(`${BACKEND_API_BASE_URL}/api/auth/google/status`, { credentials: "include" });
        const result = await response.json();
        if (!result.enabled) throw new Error("Google sign-in is not configured yet. Add the Google OAuth client ID and secret to the backend.");
        window.location.assign(`${BACKEND_API_BASE_URL}/oauth2/authorization/google`);
      } catch (error) { setStatusTone("error"); setStatus(error.message); }
    }

    return createElement("main", { className: "auth-shell" },
      createElement("section", { className: "auth-card" },
        createElement("div", { className: "brand auth-brand" }, createElement("div", { className: "brand-mark" }, "AW"), createElement("h1", null, "AI Wardrobe")),
        createElement("div", null,
          createElement("h2", null, mode === "login" ? "Welcome back" : mode === "googleSetup" ? "Complete your profile" : mode === "forgot" ? "Forgot your password?" : mode === "reset" ? "Choose a new password" : "Create your wardrobe"),
          createElement("p", null, mode === "googleSetup" ? "Choose a username and password for your Google account." : mode === "forgot" ? "Enter your account email and we’ll send you a reset link." : mode === "reset" ? "Your reset link can be used only once." : "Your clothes stay private to your account.")),
        createElement("form", { onSubmit: submit, className: "auth-form" },
          !["forgot", "reset"].includes(mode) ? createElement("label", null, "Username", createElement("input", { value: username, onChange: (e) => setUsername(e.target.value), autoComplete: "username", minLength: 3, maxLength: 40, required: true, autoCapitalize: "none" })) : null,
          (mode === "register" || mode === "forgot") ? createElement("label", null, "Email", createElement("input", { type: "email", value: email, onChange: (e) => setEmail(e.target.value), autoComplete: "email", maxLength: 254, required: true, autoCapitalize: "none" })) : null,
          mode === "googleSetup" ? createElement("label", null, "Google email", createElement("input", { type: "email", value: email, readOnly: true, tabIndex: -1 })) : null,
          mode !== "forgot" ? createElement(PasswordField, { label: mode === "reset" ? "New password" : "Password", value: password, onChange: setPassword, autoComplete: mode === "login" ? "current-password" : "new-password" }) : null,
          !["login", "forgot"].includes(mode) ? createElement(PasswordField, { label: "Re-enter password", value: confirmPassword, onChange: setConfirmPassword, autoComplete: "new-password" }) : null,
          !["login", "forgot"].includes(mode) ? createElement("ul", { className: "password-rules", "aria-label": "Password requirements" },
            passwordChecks.map((check) => createElement("li", { key: check.label, className: check.valid ? "valid" : "" }, check.label))) : null,
          mode === "register" ? createElement("label", { className: "human-check" }, `Human check: What is ${humanCheck.left} + ${humanCheck.right}?`, createElement("input", { type: "number", value: humanAnswer, onChange: (e) => setHumanAnswer(e.target.value), inputMode: "numeric", min: 2, max: 18, required: true })) : null,
          status ? createElement("p", { className: statusTone === "success" ? "auth-success" : "auth-error", role: "alert" }, status) : null,
          createElement("button", { className: "primary-button auth-submit", disabled: busy, type: "submit" }, busy ? "Please wait..." : mode === "login" ? "Log in" : mode === "googleSetup" ? "Complete account" : mode === "forgot" ? "Send reset link" : mode === "reset" ? "Reset password" : "Create account")
        ),
        mode === "login" ? createElement("button", { className: "auth-switch forgot-password-link", type: "button", onClick: () => { setMode("forgot"); setStatus(""); setEmail(""); } }, "Forgot password?") : null,
        (mode === "forgot" || mode === "reset") ? createElement("button", { className: "auth-switch", type: "button", onClick: () => { setMode("login"); setStatus(""); window.history.replaceState({}, document.title, window.location.pathname); } }, "Back to log in") : null,
        (mode === "login" || mode === "register") ? createElement("button", { className: "auth-switch", type: "button", onClick: () => { const nextMode = mode === "login" ? "register" : "login"; setMode(nextMode); setStatus(""); setStatusTone("error"); setConfirmPassword(""); setHumanAnswer(""); setHumanCheck({ left: Math.ceil(Math.random() * 9), right: Math.ceil(Math.random() * 9) }); } }, mode === "login" ? "New here? Create an account" : "Already have an account? Log in") : null,
        (mode === "login" || mode === "register") ? createElement("div", { className: "auth-divider" }, createElement("span", null, "or")) : null,
        (mode === "login" || mode === "register") ? createElement("button", { className: "google-button", type: "button", onClick: startGoogleLogin },
          createElement("span", { className: "google-mark", "aria-hidden": "true" }, "G"), "Continue with Google") : null
      )
    );
  }

  function PasswordField({ label, value, onChange, autoComplete }) {
    const [peeking, setPeeking] = React.useState(false);
    const stopPeeking = () => setPeeking(false);
    return createElement("label", null, label,
      createElement("span", { className: "password-input-wrap" },
        createElement("input", {
          type: peeking ? "text" : "password", value, onChange: (event) => onChange(event.target.value),
          autoComplete, minLength: 8, maxLength: 128, required: true
        }),
        createElement("button", {
          className: "password-peek", type: "button", tabIndex: 0,
          "aria-label": `Hold to peek at ${label.toLowerCase()}`,
          onPointerDown: (event) => { event.preventDefault(); setPeeking(true); },
          onPointerUp: stopPeeking, onPointerCancel: stopPeeking, onPointerLeave: stopPeeking,
          onKeyDown: (event) => { if (event.key === " " || event.key === "Enter") { event.preventDefault(); setPeeking(true); } },
          onKeyUp: stopPeeking, onBlur: stopPeeking
        }, createElement("span", { "aria-hidden": "true" }))
      )
    );
  }

  function createSampleWardrobeItemImage(name, category, colorA, colorB) {
    const canvas = document.createElement("canvas");
    canvas.width = 320;
    canvas.height = 360;
    const ctx = canvas.getContext("2d");
    const bg = ctx.createLinearGradient(0, 0, 320, 360);
    bg.addColorStop(0, "#f8fafc");
    bg.addColorStop(1, "#e2e8f0");
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, 320, 360);
    ctx.fillStyle = "#dbe4ea";
    ctx.beginPath();
    ctx.arc(250, 74, 54, 0, Math.PI * 2);
    ctx.fill();

    const garment = ctx.createLinearGradient(70, 55, 220, 260);
    garment.addColorStop(0, colorA);
    garment.addColorStop(1, colorB);
    ctx.fillStyle = garment;
    ctx.strokeStyle = "rgba(23, 32, 42, .22)";
    ctx.lineWidth = 5;

    if (category === "top") {
      drawFilledPolygon(ctx, [[158, 72], [128, 106], [98, 72], [62, 124], [92, 148], [102, 132], [102, 282], [218, 282], [218, 132], [228, 148], [258, 124], [222, 72], [184, 50]]);
      ctx.fillStyle = "#ffffff55";
      ctx.fillRect(139, 76, 40, 104);
    } else {
      drawFilledPolygon(ctx, [[108, 48], [218, 48], [236, 288], [174, 288], [158, 138], [136, 288], [74, 288], [94, 48]]);
      ctx.fillStyle = "#ffffff35";
      ctx.fillRect(154, 58, 8, 226);
    }

    ctx.fillStyle = "#334155";
    ctx.font = "700 15px Arial";
    ctx.textAlign = "center";
    ctx.fillText(name, 160, 334);
    return canvas.toDataURL("image/png");
  }

  function drawFilledPolygon(ctx, points) {
    ctx.beginPath();
    points.forEach(([x, y], index) => {
      if (index === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
  }

  ReactDOM.createRoot(document.getElementById("root")).render(createElement(WardrobeStudioApp));
})();
