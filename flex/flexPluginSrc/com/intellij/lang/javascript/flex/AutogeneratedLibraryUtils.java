package com.intellij.lang.javascript.flex;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.facet.FacetManagerListener;
import com.intellij.lang.javascript.flex.sdk.AirSdkType;
import com.intellij.lang.javascript.flex.sdk.FlexSdkType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AutogeneratedLibraryUtils {

  private static abstract class AutogeneratedLibraryHandler {

    abstract void handleLibrary(final ModifiableRootModel modifiableRootModel, final LibraryOrderEntry libraryOrderEntry);

    void handleModuleWithFlexFacets(final ModifiableRootModel modifiableRootModel, final Module module) {
    }
  }

  public static final String AUTOGENERATED_LIBRARY_PREFIX = "AUTOGENERATED library equal to Flex SDK ";

  private static boolean sdkTableListenerRegistered;

  private static final Key<FlexSdkRootsListener> flexSdkRootsListenerKey = Key.create("flex.sdk.root.listener");

  static final OrderRootType[] ORDER_ROOT_TYPES_TO_BE_AWARE_OF =
    new OrderRootType[]{OrderRootType.CLASSES, OrderRootType.SOURCES, JavadocOrderRootType.getInstance()};

  static void registerSdkTableListenerIfNeeded() {
    if (sdkTableListenerRegistered) {
      return;
    }
    else {
      sdkTableListenerRegistered = true;
    }

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Listener() {
      public void jdkAdded(final Sdk sdk) {
      }

      public void jdkRemoved(final Sdk sdk) {
        if (!(sdk.getSdkType() instanceof IFlexSdkType)) {
          return;
        }

        handleAutogeneratedLibrariesForSdk(sdk, new AutogeneratedLibraryHandler() {
          public void handleLibrary(final ModifiableRootModel modifiableRootModel, final LibraryOrderEntry libraryOrderEntry) {
            modifiableRootModel.removeOrderEntry(libraryOrderEntry);
          }

          void handleModuleWithFlexFacets(final ModifiableRootModel modifiableRootModel, final Module module) {
            if (sdk.equals(FlexUtils.getFlexSdkForFlexModuleOrItsFlexFacets(module))) {
              final FlexFacet flexFacet = FacetManager.getInstance(module).getFacetByType(FlexFacet.ID);
              assert flexFacet != null;
              flexFacet.getConfiguration().setFlexSdk(null, modifiableRootModel);
            }
          }
        }, true);
      }

      public void jdkNameChanged(final Sdk sdk, final String previousName) {
        if (!(sdk.getSdkType() instanceof IFlexSdkType)) {
          return;
        }

        handleAutogeneratedLibrariesForSdk(previousName, new AutogeneratedLibraryHandler() {
          public void handleLibrary(final ModifiableRootModel modifiableRootModel, final LibraryOrderEntry libraryOrderEntry) {
            final Library library = libraryOrderEntry.getLibrary();
            if (library != null) {
              final Library.ModifiableModel modifiableModel = library.getModifiableModel();
              modifiableModel.setName(suggestAutogeneratedLibraryName(sdk));
              modifiableModel.commit();
            }
          }
        }, false);
      }
    });
  }

  private static void handleAutogeneratedLibrariesForSdk(final Sdk sdk,
                                                         final AutogeneratedLibraryHandler handler,
                                                         final boolean invokeLater) {
    handleAutogeneratedLibrariesForSdk(sdk.getName(), handler, invokeLater);
  }

  private static void handleAutogeneratedLibrariesForSdk(final String sdkName,
                                                         final AutogeneratedLibraryHandler handler,
                                                         final boolean invokeLater) {
    for (final Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (project.isInitialized() && !project.isDefault() && !project.isDisposed()) {
        for (final Module module : ModuleManager.getInstance(project).getModules()) {
          if (module.isLoaded() && !module.isDisposed() && FacetManager.getInstance(module).getFacetByType(FlexFacet.ID) != null) {
            final Runnable runnable = new Runnable() {
              public void run() {
                final ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
                final Collection<LibraryOrderEntry> autogeneratedLibraryOrderEntries =
                  getAutogeneratedLibraryOrderEntries(modifiableRootModel);
                for (final LibraryOrderEntry libraryOrderEntry : autogeneratedLibraryOrderEntries) {
                  if (isAutogeneratedLibraryForThisSdk(libraryOrderEntry, sdkName)) {
                    handler.handleLibrary(modifiableRootModel, libraryOrderEntry);
                  }
                }
                handler.handleModuleWithFlexFacets(modifiableRootModel, module);
                if (modifiableRootModel.isChanged()) {
                  ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    public void run() {
                      modifiableRootModel.commit();
                    }
                  });
                }
                else {
                  modifiableRootModel.dispose();
                }
              }
            };
            if (invokeLater) {
              ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL, new Condition() {
                public boolean value(Object o) {
                  return module.isDisposed();
                }
              });
            }
            else {
              runnable.run();
            }
          }
        }
      }
    }
  }

  static void registerListenerThatRemovesAutogeneratedLibrary(final Module module) {
    final FacetManagerListener listener = new FacetManagerAdapter() {
      public void facetRemoved(final @NotNull Facet facet) {
        if (FacetManager.getInstance(module).getFacetsByType(FlexFacet.ID).isEmpty()) {
          removeAutogeneratedLibrary(module);
        }
      }
    };
    module.getMessageBus().connect(module).subscribe(FacetManager.FACETS_TOPIC, listener);
  }

  static void removeAutogeneratedLibrary(final Module module) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        final LibraryTable libraryTable = modifiableRootModel.getModuleLibraryTable();
        final Collection<Library> librariesToRemove = new ArrayList<Library>(); // libraryIterator doesn't support remove()
        for (final Iterator<Library> libraryIterator = libraryTable.getLibraryIterator(); libraryIterator.hasNext();) {
          final Library library = libraryIterator.next();
          if (isAutogeneratedLibrary(library)) {
            librariesToRemove.add(library);
          }
        }

        for (final Library library : librariesToRemove) {
          libraryTable.removeLibrary(library);
        }

        if (modifiableRootModel.isChanged()) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              modifiableRootModel.commit();
            }
          });
        }
        else {
          modifiableRootModel.dispose();
        }
      }
    }, ModalityState.NON_MODAL, new Condition() {
      public boolean value(Object o) {
        return module.isDisposed();
      }
    });
  }

  static void registerSdkRootsListenerIfNeeded(final Sdk flexSdk) {
    if (createAutogeneratedLibraryFor(flexSdk) && flexSdk.getUserData(flexSdkRootsListenerKey) == null) {
      final FlexSdkRootsListener listener = new FlexSdkRootsListener(flexSdk);
      flexSdk.getRootProvider().addRootSetChangedListener(listener);
      flexSdk.putUserData(flexSdkRootsListenerKey, listener);
    }
  }

  private static boolean createAutogeneratedLibraryFor(final Sdk flexSdk) {
    return flexSdk != null && (flexSdk.getSdkType() instanceof FlexSdkType || flexSdk.getSdkType() instanceof AirSdkType);
  }

  private static boolean isAutogeneratedLibraryForThisSdk(final LibraryOrderEntry libraryOrderEntry, final String sdkName) {
    return (AUTOGENERATED_LIBRARY_PREFIX + sdkName).equals(libraryOrderEntry.getLibraryName());
  }

  private static Collection<LibraryOrderEntry> getAutogeneratedLibraryOrderEntries(final ModifiableRootModel rootModel) {
    final Collection<LibraryOrderEntry> result = new ArrayList<LibraryOrderEntry>();
    for (final OrderEntry orderEntry : rootModel.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        if (isAutogeneratedLibrary(orderEntry)) {
          result.add((LibraryOrderEntry)orderEntry);
        }
      }
    }
    return result;
  }

  public static boolean isAutogeneratedLibrary(final OrderEntry orderEntry) {
    if (orderEntry instanceof LibraryOrderEntry) {
      final String libraryName = ((LibraryOrderEntry)orderEntry).getLibraryName();
      return libraryName != null && libraryName.startsWith(AUTOGENERATED_LIBRARY_PREFIX);
    }
    return false;
  }

  public static boolean isAutogeneratedLibrary(final Library library) {
    final String libraryName = library.getName();
    return libraryName != null && libraryName.startsWith(AUTOGENERATED_LIBRARY_PREFIX);
  }

  static String suggestAutogeneratedLibraryName(final Sdk sdk) {
    return AUTOGENERATED_LIBRARY_PREFIX + sdk.getName();
  }

  static void configureAutogeneratedLibraryIfNeeded(final ModifiableRootModel modifiableRootModel, final Sdk flexSdk) {
    Library autogeneratedLibraryEqualToFlexSdk = null;
    final LibraryTable libraryTable = modifiableRootModel.getModuleLibraryTable();
    final Collection<Library> librariesToRemove = new ArrayList<Library>(); // libraryIterator doesn't support remove()
    for (Iterator<Library> libraryIterator = libraryTable.getLibraryIterator(); libraryIterator.hasNext();) {
      final Library library = libraryIterator.next();
      if (isAutogeneratedLibrary(library)) {
        if (createAutogeneratedLibraryFor(flexSdk) && suggestAutogeneratedLibraryName(flexSdk).equals(library.getName())) {
          autogeneratedLibraryEqualToFlexSdk = library;
        }
        else { // SDK was changed.
          librariesToRemove.add(library);
        }
      }
    }

    for (final Library library : librariesToRemove) {
      libraryTable.removeLibrary(library);
    }

    if (createAutogeneratedLibraryFor(flexSdk)) {
      if (autogeneratedLibraryEqualToFlexSdk == null) {
        autogeneratedLibraryEqualToFlexSdk = libraryTable.createLibrary(suggestAutogeneratedLibraryName(flexSdk));
      }

      makeLibraryEqualToSdk(autogeneratedLibraryEqualToFlexSdk, flexSdk);
    }
  }

  static void makeLibraryEqualToSdk(final Library library, final Sdk flexSdk) {
    final RootProvider libraryRootProvider = library.getRootProvider();
    final RootProvider sdkRootProvider = flexSdk.getRootProvider();

    boolean libAndSdkEqual = true;
    for (OrderRootType orderRootType : ORDER_ROOT_TYPES_TO_BE_AWARE_OF) {
      if (!rootsEqual(libraryRootProvider, sdkRootProvider, orderRootType)) {
        libAndSdkEqual = false;
        break;
      }
    }

    if (!libAndSdkEqual) {
      final Library.ModifiableModel libraryModifiableModel = library.getModifiableModel();
      for (final OrderRootType orderRootType : ORDER_ROOT_TYPES_TO_BE_AWARE_OF) {
        for (final String url : libraryModifiableModel.getUrls(orderRootType)) {
          libraryModifiableModel.removeRoot(url, orderRootType);
        }
        for (final String url : sdkRootProvider.getUrls(orderRootType)) {
          libraryModifiableModel.addRoot(url, orderRootType);
        }
      }
      libraryModifiableModel.commit();
    }
  }

  static boolean rootsEqual(final RootProvider rootProvider1, final RootProvider rootProvider2, final OrderRootType orderRootType) {
    return Arrays.equals(rootProvider1.getUrls(orderRootType), rootProvider2.getUrls(orderRootType));
  }

  private static class FlexSdkRootsListener implements RootProvider.RootSetChangedListener {
    private final Sdk myFlexSdk;

    private FlexSdkRootsListener(final Sdk flexSdk) {
      myFlexSdk = flexSdk;
    }

    public void rootSetChanged(final RootProvider wrapper) {
      handleAutogeneratedLibrariesForSdk(myFlexSdk, new AutogeneratedLibraryHandler() {
        public void handleLibrary(ModifiableRootModel modifiableRootModel, LibraryOrderEntry libraryOrderEntry) {
          final Library library = libraryOrderEntry.getLibrary();
          if (library != null) {
            makeLibraryEqualToSdk(library, myFlexSdk);
          }
        }
      }, false);
    }
  }

}
